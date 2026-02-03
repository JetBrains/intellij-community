// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.isVowel;

/**
 * @author Bas Leijdekkers
 */
@ApiStatus.Internal
public final class PastParticiple {

  private static final int IRREGULAR_SIZE = 175;
  private static final Map<String, String> IRREGULAR_VERBS = CollectionFactory.createCaseInsensitiveStringMap(IRREGULAR_SIZE);
  private static final int DOUBLED_SIZE = 444;
  private static final Set<String> DOUBLED_FINAL_CONSONANTS = CollectionFactory.createCaseInsensitiveStringSet(DOUBLED_SIZE);
  private static final String[] PREFIXES = {"be", "co", "de", "dis", "for", "fore", "inter", "mis", "out", "over", "pre", "re", "un", "under", "up"};

  /**
   * Generates past participle form of an English verb. 
   * E.g. hang -> hung, resolve -> resolved, add -> added, open -> opened
   * 
   * @param verb  verb in first person present form
   * @return the past participle conjugation of the verb
   */
  public static String pastParticiple(String verb) {
    if (ignore(verb)) return verb;
    String irregularVerb = getIrregularPastParticiple(verb);
    if (irregularVerb != null) return Pluralizer.restoreCase(verb, irregularVerb);
    String pastParticiple = getDoubledFinalConsonantPastParticiple(verb);
    if (pastParticiple != null) return Pluralizer.restoreCase(verb, pastParticiple);
    return Pluralizer.restoreCase(verb, generateHeuristicPastParticiple(verb));
  }

  private static String getDoubledFinalConsonantPastParticiple(String verb) {
    String pastParticiple = generateHeuristicDoubledFinalConsonantPastParticiple(verb);
    if (pastParticiple != null) return pastParticiple;
    if (DOUBLED_FINAL_CONSONANTS.contains(verb)) return verb + verb.charAt(verb.length() - 1) + "ed";
    for (String prefix : PREFIXES) {
      if (verb.startsWith(prefix) && DOUBLED_FINAL_CONSONANTS.contains(verb.substring(prefix.length()))) {
        return verb + verb.charAt(verb.length() - 1) + "ed";
      }
    }
    return null;
  }

  private static @Nullable String generateHeuristicDoubledFinalConsonantPastParticiple(String verb) {
    int length = verb.length();
    if (length < 3) return null;
    char c1 = toLowerCase(verb.charAt(length - 1));
    if (c1 != 'x' && c1 != 'y' && c1 != 'w') {
      char c2 = toLowerCase(verb.charAt(length - 2));
      if (!isVowel(c1) && isVowel(c2)) {
        char c3 = toLowerCase(verb.charAt(length - 3));
        if (!isVowel(c3) || c3 == 'y') {
          if (length == 3 || length == 4 && !isVowel(toLowerCase(verb.charAt(0))) ||
              length == 5 && !isVowel(toLowerCase(verb.charAt(0))) && !isVowel(toLowerCase(verb.charAt(1)))) {
            return verb + c1 + "ed";
          }
        }
        else if (length > 3 && c3 == 'u' && toLowerCase(verb.charAt(length - 4)) == 'q') {
          return verb + c1 + "ed";
        }
      }
    }
    return null;
  }

  private static String getIrregularPastParticiple(String verb) {
    String irregularVerb = IRREGULAR_VERBS.get(verb);
    if (irregularVerb != null) return irregularVerb;
    for (String prefix : PREFIXES) {
      if (verb.startsWith(prefix)) {
        irregularVerb = IRREGULAR_VERBS.get(verb.substring(prefix.length()));
        if (irregularVerb !=  null) return prefix + irregularVerb;
      }
    }
    return null;
  }

  /**
   * Uses heuristics to generate the past participle form of the specified English verb.
   * 
   * @param verb  verb in first person present form
   * @return the past participle conjugation of the verb
   */
  private static String generateHeuristicPastParticiple(String verb) {
    int length = verb.length();
    char c1 = toLowerCase(verb.charAt(length - 1));
    if (c1 == 'e') return verb + 'd';
    char c2 = toLowerCase(verb.charAt(length - 2));
    if (c1 == 'y' && !isVowel(c2)) return verb.substring(0, length - 1) + "ied";
    return c1 == 'c' && isVowel(c2) ? verb + "ked" : verb + "ed";
  }

  private static boolean ignore(String verb) {
    int length = verb.length();
    if (length < 2) return true;
    if (verb.equals("of")) return true;
    char c1 = toLowerCase(verb.charAt(length - 1));
    char c2 = toLowerCase(verb.charAt(length - 2));
    if (c1 == 's' && (c2 == 'e' || c2 == 'l')) return true;
    for (int i = 0; i < length; i++) {
      if (!isAsciiLetter(verb.charAt(i))) return true;
    }
    return false;
  }

  private static boolean isAsciiLetter(char c) {
    return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
  }

  private static char toLowerCase(char c) {
    return (char)(c | 0x20); // cheap hack
  }

  static {
    String[] irregularVerbs = new String[] {
      "abide", "abode",
      "arise", "arisen",
      "awake", "awoken",
      "be", "been",
      "bear", "borne",
      "beat", "beaten",
      "begin", "begun",
      "bend", "bent",
      "bet", "bet",
      "bid", "bid", // can also be "bidden"
      "bind", "bound",
      "bite", "bitten",
      "bleed", "bled",
      "blow", "blown",
      "break", "broken",
      "breed", "bred",
      "bring", "brought",
      "broadcast", "broadcast",
      "build", "built",
      "burn", "burnt",
      "burst", "burst",
      "bust", "bust",
      "buy", "bought",
      "can", "could",
      "cast", "cast",
      "catch", "caught",
      "chide", "chidden",
      "choose", "chosen",
      "cling", "clung",
      //"clothe", "clad",
      "come", "come",
      "cost", "cost",
      "creep", "crept",
      "cut", "cut",
      "deal", "dealt",
      "dig", "dug",
      //"dive", "dove",
      "do", "done",
      "draw", "drawn",
      "dream", "dreamt",
      "drink", "drunk",
      "drive", "driven",
      //"dwell", "dwelt",
      "eat", "eaten",
      "fall", "fallen",
      "feed", "fed",
      "feel", "felt",
      "fight", "fought",
      "find", "found",
      "flee", "fled",
      "fling", "flung",
      "fly", "flown",
      "forbid", "forbidden",
      "forsake", "forsaken",
      "freeze", "frozen",
      "get", "gotten",
      "give", "given",
      "grind", "ground",
      "go", "gone",
      "grow", "grown",
      "hang", "hung",
      "have", "had",
      "hear", "heard",
      "hide", "hidden",
      "hit", "hit",
      "hold", "held",
      "hurt", "hurt",
      "keep", "kept",
      "kneel", "knelt",
      "know", "known",
      "lay", "laid",
      "lead", "led",
      //"lean", "leant",
      //"leap", "leapt",
      //"learn", "learnt",
      "leave", "left",
      "lend", "lent",
      "let", "let",
      //"lie", "lain", // depends on meaning
      "light", "lit",
      "lose", "lost",
      "make", "made",
      "mean", "meant",
      "meet", "met",
      "misunderstand", "misunderstood", // two prefixes!
      "mow", "mown",
      "offset", "offset",
      "partake", "partaken",
      "pay", "paid",
      //"plead", "pled",
      "prove", "proven",
      "proofread", "proofread",
      "put", "put",
      "quit", "quit",
      "read", "read",
      "rend", "rent",
      "rid", "rid",
      "ride", "ridden",
      "ring", "rung",
      "rise", "risen",
      "roughcast", "roughcast",
      "run", "run",
      //"saw", "sawn",
      "say", "said",
      "see", "seen",
      "seek", "sought",
      "sell", "sold",
      "send", "sent",
      "set", "set",
      "sew", "sewn",
      "shake", "shaken",
      "shave", "shaven",
      "shear", "shorn",
      "shed", "shed",
      "shine", "shone",
      "shoe", "shod",
      "shoot", "shot",
      "show", "shown",
      "shrink", "shrunk",
      "shut", "shut",
      "sing", "sung",
      "sink", "sunk",
      "sit", "sat",
      "slay", "slain",
      "sleep", "slept",
      "slide", "slid",
      "sling", "slung",
      "slink", "slunk",
      "slit", "slit",
      //"smell", "smelt", // archaic
      "sneak", "snuck",
      "sow", "sown",
      "speak", "spoken",
      "speed", "sped",
      //"spell", "spelt", // archaic
      "spend", "spent",
      "spill", "spilt",
      "spin", "spun",
      "spit", "spat",
      "split", "split",
      "spoil", "spoilt",
      "spread", "spread",
      "spring", "sprung",
      "stand", "stood",
      "steal", "stolen",
      "stick", "stuck",
      "sting", "stung",
      "stink", "stunk",
      "strew", "strewn",
      "stride", "stridden",
      "strike", "stricken", // "struck" when meaning is "hit" as well.
      "string", "strung",
      "strive", "striven",
      "sublet", "sublet",
      "swear", "sworn",
      "sweat", "sweat",
      "sweep", "swept",
      "swell", "swollen",
      "swim", "swum",
      "swing", "swung",
      "take", "taken",
      "teach", "taught",
      "tear", "torn",
      "telecast", "telecast",
      "tell", "told",
      "think", "thought",
      //"thrive", "thriven",
      "throw", "thrown",
      "thrust", "thrust",
      "tread", "trodden",
      "typecast", "typecast",
      "typeset", "typeset",
      "typewrite", "typewritten",
      "underlie", "underlain",
      "wake", "woken",
      "waylay", "waylaid",
      "wear", "worn",
      "weave", "woven",
      "weep", "wept",
      "wet", "wet",
      "win", "won",
      "wind", "wound",
      "withdraw", "withdrawn",
      "withhold", "withheld",
      "withstand", "withstood",
      "wring", "wrung",
      "write", "written"
    };
    assert irregularVerbs.length / 2 == IRREGULAR_SIZE;
    for (int i = 0, length = irregularVerbs.length; i < length; i += 2) {
      String present = irregularVerbs[i];
      String pastParticiple = irregularVerbs[i + 1];
      if (pastParticiple.equals(generateHeuristicPastParticiple(present))) {
        throw new IllegalStateException("unnecessary entry: " + present);
      }
      if (IRREGULAR_VERBS.containsKey(present)) {
        throw new IllegalStateException("duplicated entry: " + present);
      }
      IRREGULAR_VERBS.put(present, pastParticiple);
    }
    for (String irregularVerb : IRREGULAR_VERBS.keySet()) {
      for (String prefix : PREFIXES) {
        if (IRREGULAR_VERBS.containsKey(prefix + irregularVerb) &&
            IRREGULAR_VERBS.get(prefix + irregularVerb).equals(prefix + IRREGULAR_VERBS.get(irregularVerb))) {
          throw new IllegalStateException("unnecessary prefix entry: " + prefix + irregularVerb);
        }
      }
    }
    String[] doubledFinalConsonants =
      new String[]{"abet", "abhor", "abut", "adlib", "admit", "aerobat", "aerosol", "airdrop", "allot", "anagram", "annul", "appal",
        "apparel", "armbar", "aver", "babysit", "backdrop", "backflip", "backlog", "backpedal", "backslap", "backstab", "ballot", "barbel",
        "bareleg", "barrel", "bayonet", "befit", "befog", "benefit", "besot", "bestir", "bevel", "bewig", "billet", "bitmap", "blackleg",
        "bloodlet", "bobsled", "bodypop", "boobytrap", "bootleg", "bowel", "bracket", "buffet", "bullshit", "cabal", "cancel", "caracol",
        "caravan", "carburet", "carnap", "carol", "carpetbag", "castanet", "catnap", "cavil", "chanel", "channel", "chargecap", "chirrup",
        "chisel", "clearcut", "clodhop", "closet", "cobweb", "coif", "combat", "commit", "compel", "concur", "confab", "confer", "control",
        "coral", "corbel", "corral", "cosset", "costar", "councel", "council", "counsel", "counterplot", "courtmartial", "crossleg",
        "cudgel", "daysit", "deadpan", "debag", "debar", "debug", "defer", "defog", "degas", "demit", "demob", "demur", "denet", "depig",
        "depip", "depit", "derig", "deter", "devil", "diagram", "dial", "disbar", "disbud", "discomfit", "disembowel", "dishevel", "dispel",
        "distil", "dognap", "doorstep", "dowel", "driftnet", "drivel", "duel", "dybbuk", "earwig", "eavesdrop", "ecolabel", "egotrip",
        "electroblot", "embed", "emit", "empanel", "enamel", "enrol", "enthral", "entrammel", "entrap", "enwrap", "estop", "excel", "expel",
        "extol", "farewel", "featherbed", "fingertip", "focus", "footslog", "format", "foxtrot", "fuel", "fulfil", "fullyfit", "funnel",
        "gambol", "garrot", "giftwrap", "gimbal", "globetrot", "goldpan", "golliwog", "goosestep", "gossip", "gravel", "groundhop",
        "grovel", "gunrun", "haircut", "handbag", "handicap", "handknit", "handset", "hareleg", "hedgehop", "hiccup", "hobnob", "horsewhip",
        "hostel", "hotdog", "hovel", "humbug", "hushkit", "illfit", "imbed", "immunoblot", "impel", "imperil", "incur", "infer", "initial",
        "input", "inset", "inspan", "instal", "instil", "inter", "interbed", "intercrop", "intermit", "interwar",
        "japan", "jawdrop", "jetlag", "jewel", "jitterbug", "jogtrot", "kennel", "kidnap", "kissogram", "kneecap", "label", "lavel",
        "leafcut", "leapfrog", "level", "libel", "lollop", "longleg", "mackerel", "mahom", "manumit", "marshal", "marvel", "matchwin",
        "metal", "microplan", "microprogram", "milksop", "misclub", "model", "monogram", "multilevel", "nightclub", "nightsit", "nonplus",
        "nutmeg", "occur", "offput", "omit", "onlap", "outcrop", "outfit", "outgas", "outgeneral", "outgun", "outjab", "outplan", "outship",
        "outshop", "outsin", "outspan", "outstrip", "outwit", "overcrap", "overcrop", "overdub", "overfit", "overhat", "overlap", "overman",
        "overpet", "overplot", "overshop", "overstep", "overtip", "overtop", "panel", "paperclip", "parallel", "parcel", "patrol", "pedal",
        "peewit", "pencil", "permit", "petal", "pettifog", "photoset", "photostat", "phototypeset", "picket", "pilot", "pipefit", "pipet",
        "plummet", "policyset", "ponytrek", "pouf", "prebag", "prefer", "preplan", "prizewin", "profer", "program", "propel", "pummel",
        "pushfit", "quarrel", "quickskim", "quickstep", "quickwit", "quivertip", "rabbit", "radiolabel", "ramrod", "ratecap", "ravel",
        "rebel", "rebin", "rebut", "recap", "recrop", "recur", "refer", "refit", "reflag", "refret", "regret", "rehab", "rejig", "rekit",
        "reknot", "relap", "remap", "remit", "repastel", "repel", "repin", "replan", "replot", "replug", "repol", "repot", "rerig",
        "reskin", "retop", "retrim", "retrofit", "revel", "revet", "rewrap", "ricochet", "ringlet", "rival", "rivet", "roadrun", "rocket",
        "roset", "rosin", "rowel", "runnel", "sandbag", "scalpel", "schlep", "semicontrol", "semiskim", "sentinel", "shopfit", "shovel",
        "shrinkwrap", "shrivel", "sideslip", "sidestep", "signal", "sinbin", "slowclap", "snivel", "snorkel", "softpedal", "spiderweb",
        "spiral", "spraygun", "springtip", "squirrel", "stencil", "subcrop", "submit", "subset", "suedetrim", "sulfuret", "summit",
        "suntan", "swivel", "tassel", "teleshop", "tendril", "thermal", "thermostat", "tightlip", "tinsel", "tittup", "toecap", "tomorrow",
        "total", "towel", "traget", "trainspot", "trammel", "transfer", "transit", "transmit", "transship", "travel", "trendset", "trepan",
        "tripod", "trousseaushop", "trowel", "tunnel", "unban", "unbar", "unbob", "uncap", "unclip", "undam", "underfit", "underman",
        "underpin", "unfit", "unknot", "unlip", "unman", "unpad", "unpeg", "unpin", "unplug", "unrip", "unsnap", "unstep", "unstir",
        "unstop", "untap", "unwrap", "unzip", "up", "verbal", "victual", "wainscot", "waterlog", "weasel", "whiteskin", "wildcat",
        "wiretap", "woodchop", "woodcut", "worship", "yarnspin", "yodel", "zigzag"};
    assert doubledFinalConsonants.length == DOUBLED_SIZE;
    for (String verb : doubledFinalConsonants) {
      int length = verb.length();
      char lastLetter = verb.charAt(length - 1);
      if (isVowel(lastLetter) || verb.charAt(length - 2) == lastLetter) {
        throw new IllegalStateException("incorrect entry: " + verb);
      }
      if (getIrregularPastParticiple(verb) != null) {
        throw new IllegalStateException("irregular verb: " + verb);
      }
      String pastParticiple = verb + lastLetter + "ed";
      if (pastParticiple.equals(generateHeuristicPastParticiple(verb)) ||
          pastParticiple.equals(getDoubledFinalConsonantPastParticiple(verb))) {
        throw new IllegalStateException("unnecessary entry: " + verb);
      }
      if (pastParticiple(verb).equals(pastParticiple)) {
        throw new IllegalStateException("duplicated entry: " + verb);
      }
      if (!DOUBLED_FINAL_CONSONANTS.add(verb)) {
        throw new IllegalStateException("duplicate entry: " + verb);
      }
    }
    for (String word : DOUBLED_FINAL_CONSONANTS) {
      for (String prefix : PREFIXES) {
        if (DOUBLED_FINAL_CONSONANTS.contains(prefix + word)) {
          throw new IllegalStateException("unnecessary prefix entry: " + prefix + word);
        }
      }
    }
  }
}
