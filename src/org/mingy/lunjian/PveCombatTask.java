package org.mingy.lunjian;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mingy.lunjian.skills.Skills;

public class PveCombatTask extends TimerTask {

	private static Pattern[] PART_FINISH_PATTERNS = new Pattern[] {
		Pattern.compile("^(.*)顿时被冲开老远，失去了攻击之势！$"),
		Pattern.compile("^(.*)被(.*)的真气所迫，只好放弃攻击！$"),
		Pattern.compile("^(.*)衣裳鼓起，真气直接将(.*)逼开了！$"),
		Pattern.compile("^(.*)找到了闪躲的空间！$"),
		Pattern.compile("^(.*)朝边上一步闪开！$"),
		Pattern.compile("^面对(.*)的攻击，(.*)毫不为惧！$"),
		Pattern.compile("^（(.*)似乎受了点轻伤，不过光从外表看不大出来。）$"),
		Pattern.compile("^（(.*)受了几处伤，不过似乎并不碍事。）$"),
		Pattern.compile("^（(.*)看起来可能受了点轻伤。）$"),
		Pattern.compile("^（(.*)受伤不轻，看起来状况并不太好。）$"),
		Pattern.compile("^（(.*)气息粗重，动作开始散乱，看来所受的伤着实不轻。）$"),
		Pattern.compile("^（(.*)已经伤痕累累，正在勉力支撑着不倒下去。）$"),
		Pattern.compile("^（(.*)受了相当重的伤，只怕会有生命危险。）$"),
		Pattern.compile("^（(.*)伤重之下已经难以支撑，眼看就要倒在地上。）$"),
		Pattern.compile("^（(.*)受伤过重，已经奄奄一息，命在旦夕了。）$"),
		Pattern.compile("^（(.*)受伤过重，已经有如风中残烛，随时都可能断气。）$") };

	private static Pattern[] COMBO_ATTACK_PATTERNS = new Pattern[] {
			Pattern.compile("^(.*)招式之间组合成了更为凌厉的攻势！$"),
			Pattern.compile("^(.*)这几招配合起来，威力更为惊人！$"),
			Pattern.compile("^(.*)将招式连成一片，令(.*)眼花缭乱！$") };

	private static Pattern POZHAO_PATTERN1 = Pattern
			.compile("^(.*)的招式尽数被(.*)所破！$");
	private static Pattern POZHAO_PATTERN2 = Pattern
			.compile("^(.*)这一招正好击向了(.*)的破绽！$");
	private static Pattern POZHAO_PATTERN3 = Pattern
			.compile("^(.*)一不留神，招式被(.*)所破！$");
	private static Pattern POZHAO_PATTERN4 = Pattern
			.compile("^(.*)的对攻无法击破(.*)的攻势，处于明显下风！$");
	private static Pattern POZHAO_PATTERN5 = Pattern
			.compile("^(.*)的招式并未有明显破绽，(.*)只好放弃对攻！$");
	private static Pattern POZHAO_PATTERN6 = Pattern
			.compile("^(.*)这一招并未奏效，仍被(.*)招式紧逼！$");

	private static Pattern SKILL_CHAIN_PATTERN = Pattern
			.compile("^\\-\\-(.*)\\-\\-(.*)\\-\\-$");

	private static String[] PREFIX_COMBO = new String[] { "紧接着，", "迅疾无比，",
			"身形再转，" };

	private static Map<String, String[]> SKILL_MAP1 = new HashMap<String, String[]>();
	private static Map<String, String[]> SKILL_MAP2 = new HashMap<String, String[]>();

	static {
		SKILL_MAP1.put("九天龙吟剑法", new String[] { "排云掌法", "如来神掌" });
		SKILL_MAP1.put("覆雨剑法", new String[] { "排云掌法", "如来神掌" });
		SKILL_MAP1.put("织冰剑法", new String[] { "排云掌法", "如来神掌" });
		SKILL_MAP1.put("排云掌法", new String[] { "雪饮狂刀", "翻云刀法" });
		SKILL_MAP1.put("如来神掌", new String[] { "雪饮狂刀", "翻云刀法" });
		SKILL_MAP1.put("雪饮狂刀", new String[] { "九天龙吟剑法", "覆雨剑法", "织冰剑法" });
		SKILL_MAP1.put("翻云刀法", new String[] { "九天龙吟剑法", "覆雨剑法", "织冰剑法" });
		SKILL_MAP2.put("九天龙吟剑法", new String[] { "排云掌法", "雪饮狂刀" });
		SKILL_MAP2.put("覆雨剑法", new String[] { "翻云刀法", "如来神掌" });
		SKILL_MAP2.put("织冰剑法", new String[] { "孔雀翎", "飞刀绝技" });
		SKILL_MAP2.put("排云掌法", new String[] { "九天龙吟剑法", "雪饮狂刀" });
		SKILL_MAP2.put("如来神掌", new String[] { "覆雨剑法", "孔雀翎" });
		SKILL_MAP2.put("雪饮狂刀", new String[] { "九天龙吟剑法", "排云掌法" });
		SKILL_MAP2.put("翻云刀法", new String[] { "覆雨剑法", "飞刀绝技" });
		SKILL_MAP2.put("飞刀绝技", new String[] { "翻云刀法", "织冰剑法" });
		SKILL_MAP2.put("孔雀翎", new String[] { "如来神掌", "织冰剑法" });
	}

	private CommandLine cmdline;
	private Map<String, List<Posture>> all_skills = new HashMap<String, List<Posture>>();
	private String[] performs;
	private Part part;

	public PveCombatTask(CommandLine cmdline) {
		this.cmdline = cmdline;
	}

	public boolean init() {
		try {
			loadSkills();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		String str = cmdline.getProperty("pk.perform");
		if (str != null && str.trim().length() > 0) {
			performs = str.trim().split(",");
		} else {
			System.out.println("property pk.perform not set");
			performs = new String[0];
		}
		return true;
	}

	private void loadSkills() throws IOException {
		all_skills.clear();
		for (String name : Skills.SKILL_FILES.keySet()) {
			InputStream in = CommandLine.class.getResourceAsStream("skills/"
					+ Skills.SKILL_FILES.get(name) + ".desc");
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					in, "utf-8"));
			List<Posture> postures = new ArrayList<Posture>();
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() > 0) {
					Posture posture = new Posture();
					StringBuilder sb = new StringBuilder("^");
					int i = 0, j = 0, k;
					while ((k = line.indexOf('{', j)) >= 0) {
						int l = line.indexOf('}', k + 1);
						if (l < 0) {
							break;
						}
						String s = line.substring(k, l + 1);
						sb.append(line.substring(j, k));
						if ("{$p}".equals(s)) {
							sb.append("(左手|右手|后心|左耳|右耳|两肋|左肩|右肩|左腿|右腿|左臂|右臂|腰间|左脸|右脸|小腹|颈部|头顶|左脚|右脚|胸口)");
						} else {
							sb.append("(.*)");
						}
						j = l + 1;
						++i;
						if (posture.source == 0 && "{$s}".equals(s)) {
							posture.source = i;
						} else if (posture.target == 0 && "{$t}".equals(s)) {
							posture.target = i;
						}
					}
					sb.append(line.substring(j)).append("$");
					posture.pattern = Pattern.compile(sb.toString(),
							Pattern.CANON_EQ | Pattern.UNICODE_CASE);
					posture.lines = line.split("\\\\n").length;
					postures.add(posture);
				}
			}
			reader.close();
			all_skills.put(name, postures);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		try {
			Map<String, Object> result = (Map<String, Object>) cmdline
					.js(cmdline.load("get_combat_msgs.js"));
			if (result == null) {
				part = null;
				return;
			}
			List<String> msgs = (List<String>) result.get("msgs");
			for (int i = msgs.size() - 1; i >= 0; i--) {
				String msg = msgs.get(i);
				boolean matched = false;
				for (Pattern pattern : PART_FINISH_PATTERNS) {
					if (pattern.matcher(msg).matches()) {
						System.out.println("[VS] " + msg);
						msgs = msgs.subList(i + 1, msgs.size());
						part = null;
						matched = true;
						break;
					}
				}
				if (matched) {
					break;
				}
			}
			if (!msgs.isEmpty()) {
				if (part == null) {
					part = new Part();
				}
				Stack<String> skills = new Stack<String>();
				for (int i = 0; i < msgs.size(); i++) {
					String msg = msgs.get(i);
					Matcher m = SKILL_CHAIN_PATTERN.matcher(msg);
					if (m.find()) {
						skills.push(m.group(2));
						skills.push(m.group(1));
						System.out.println("[VS] " + m.group());
					} else {
						PoZhao p = checkPoZhao(msg);
						if (p != null) {
							if ("你".equals(p.attacker)) {
								p.attacker = (String) result.get("me");
							} else if ("你".equals(p.defender)) {
								p.defender = (String) result.get("me");
							}
							if (p.success) {
								part.attacker = p.attacker;
								part.defender = p.defender;
								part.combo_attack = false;
								part.skills.clear();
								part.skills.addAll(skills);
							} else {
								part.attacker = p.defender;
							}
							System.out.println("[VS] " + p.attacker + " po "
									+ p.defender + " "
									+ (p.success ? "ok" : "fail") + " ("
									+ part.attacker + " -> " + part.defender
									+ ")");
							part.performed = false;
							skills.clear();
						} else {
							boolean matched = false;
							if (part.attacker == null && part.defender == null) {
								String msg1 = msg.replace('\u00a0', ' ');
								for (String prefix : PREFIX_COMBO) {
									if (msg1.startsWith(prefix)) {
										msg1 = msg1.substring(prefix.length());
										break;
									}
								}
								for (String name : all_skills.keySet()) {
									List<Posture> postures = all_skills
											.get(name);
									if (postures != null) {
										for (Posture posture : postures) {
											String text = msg1;
											if (posture.lines > 1) {
												if (msgs.size() - i < posture.lines) {
													continue;
												}
												StringBuilder sb = new StringBuilder(
														msg);
												for (int j = 1; j < posture.lines; j++) {
													sb.append("\n").append(
															msgs.get(i + j));
												}
												text = sb.toString();
											}
											m = posture.pattern.matcher(text);
											if (m.find()) {
												skills.add(name);
												if (posture.source > 0) {
													part.attacker = m
															.group(posture.source);
												}
												if (posture.target > 0) {
													part.defender = m
															.group(posture.target);
												}
												if ("你".equals(part.attacker)) {
													part.attacker = (String) result
															.get("me");
												} else if ("你"
														.equals(part.defender)) {
													part.defender = (String) result
															.get("me");
												}
												part.combo_attack = false;
												System.out.println("[VS] "
														+ part.attacker
														+ " attack" + " ("
														+ part.attacker
														+ " -> "
														+ part.defender + ")");
												part.performed = false;
												matched = true;
												break;
											}
										}
									}
									if (matched) {
										break;
									}
								}
							}
							if (!matched) {
								for (Pattern pattern : COMBO_ATTACK_PATTERNS) {
									m = pattern.matcher(msg);
									if (m.find()) {
										String attacker = m.group(1);
										String defender = m.groupCount() > 1 ? m
												.group(2) : null;
										if ("你".equals(attacker)) {
											attacker = (String) result
													.get("me");
										} else if ("你".equals(defender)) {
											defender = (String) result
													.get("me");
										}
										if (part.attacker == null) {
											part.attacker = attacker;
										}
										if (part.defender == null) {
											part.defender = defender;
										}
										part.combo_attack = true;
										part.skills.addAll(skills);
										System.out.println("[VS] " + attacker
												+ " attack combo" + " ("
												+ part.attacker + " -> "
												+ part.defender + ")");
										part.performed = false;
										skills.clear();
										break;
									}
								}
							}
						}
					}
				}
			}
			if (part != null && part.attacker != null && !part.performed) {
				boolean attacker_in_my_side = inMySide(part.attacker, result);
				long pt = Math.round(Double.parseDouble(String.valueOf(result
						.get("pt"))));
				boolean do_attack = false;
				boolean do_combo = false;
				if (result.get("me").equals(part.defender)) { // defender is me
					do_attack = true;
				} else if (!attacker_in_my_side) { // defender in my side
					do_attack = true;
				} else { // attacker in my side
					if (pt >= 9) {
						do_attack = true;
						do_combo = true;
					}
				}
				if (do_attack) {
					List<String> pfms = (List<String>) result.get("pfms");
					while (!part.performed && !part.skills.isEmpty()) {
						String[] choose = !do_combo ? SKILL_MAP1
								.get(part.skills.pop()) : SKILL_MAP2
								.get(part.skills.pop());
						if (choose != null) {
							for (String pfm : choose) {
								int i = pfms.indexOf(pfm);
								if (i >= 0) {
									System.out.println("[VS] perform " + pfm);
									cmdline.sendCmd("playskill " + (i + 1));
									part.performed = true;
									break;
								}
							}
						}
					}
					if (!part.performed) {
						for (String pfm : performs) {
							int i = pfms.indexOf(pfm);
							if (i >= 0) {
								System.out.println("[VS] perform " + pfm);
								cmdline.sendCmd("playskill " + (i + 1));
								part.performed = true;
								break;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			cmdline.stopTask(this);
		}
	}

	@SuppressWarnings("unchecked")
	private boolean inMySide(String name, Map<String, Object> result) {
		if ("你".equals(name)) {
			return true;
		}
		String me = (String) result.get("me");
		List<String> vs = (List<String>) result.get("vs1");
		if (vs.contains(me) && vs.contains(name)) {
			return true;
		}
		vs = (List<String>) result.get("vs2");
		if (vs.contains(me) && vs.contains(name)) {
			return true;
		}
		return false;
	}

	private PoZhao checkPoZhao(String msg) {
		Matcher m = POZHAO_PATTERN1.matcher(msg);
		if (m.find()) {
			PoZhao p = new PoZhao();
			p.success = true;
			p.attacker = m.group(2);
			p.defender = m.group(1);
			return p;
		}
		m = POZHAO_PATTERN2.matcher(msg);
		if (m.find()) {
			PoZhao p = new PoZhao();
			p.success = true;
			p.attacker = m.group(1);
			p.defender = m.group(2);
			return p;
		}
		m = POZHAO_PATTERN3.matcher(msg);
		if (m.find()) {
			PoZhao p = new PoZhao();
			p.success = true;
			p.attacker = m.group(2);
			p.defender = m.group(1);
			return p;
		}
		m = POZHAO_PATTERN4.matcher(msg);
		if (m.find()) {
			PoZhao p = new PoZhao();
			p.success = false;
			p.attacker = m.group(1);
			p.defender = m.group(2);
			return p;
		}
		m = POZHAO_PATTERN5.matcher(msg);
		if (m.find()) {
			PoZhao p = new PoZhao();
			p.success = false;
			p.attacker = m.group(2);
			p.defender = m.group(1);
			return p;
		}
		m = POZHAO_PATTERN6.matcher(msg);
		if (m.find()) {
			PoZhao p = new PoZhao();
			p.success = false;
			p.attacker = m.group(1);
			p.defender = m.group(2);
			return p;
		}
		return null;
	}

	private static class Part {
		String attacker;
		String defender;
		boolean combo_attack;
		Stack<String> skills = new Stack<String>();
		boolean performed;
	}

	private static class PoZhao {
		boolean success;
		String attacker;
		String defender;
	}

	private static class Posture {
		Pattern pattern;
		int lines;
		int source;
		int target;
	}
}
