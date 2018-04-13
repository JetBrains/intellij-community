/*
 * The original license from http://github.com/blakeembrey/pluralize:
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Blake Embrey (hello@blakeembrey.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.notNullize;

/**
 * A java version of http://github.com/blakeembrey/pluralize
 * Revision: de7b35e700caccfb4e74923d98ab9b40006eda04 (Nov 19, 2017)
 *
 * It tries to preserve the original structure for future sync.
 *
 * Other sources of inspiration:
 * http://www.csse.monash.edu.au/~damian/papers/HTML/Plurals.html
 * (a java implementation: https://github.com/atteo/evo-inflector)
 *
 * @author gregsh
 */
class Pluralizer {

  static final Pluralizer PLURALIZER;

  private final Map<String, String> irregularSingles = ContainerUtil.newTroveMap(CaseInsensitiveStringHashingStrategy.INSTANCE);
  private final Map<String, String> irregularPlurals = ContainerUtil.newTroveMap(CaseInsensitiveStringHashingStrategy.INSTANCE);
  private final Set<String> uncountables = ContainerUtil.newTroveSet(CaseInsensitiveStringHashingStrategy.INSTANCE);
  private final List<Pair<Pattern, String>> pluralRules = ContainerUtil.newArrayList();
  private final List<Pair<Pattern, String>> singularRules = ContainerUtil.newArrayList();

  /**
   * Pass in a word token to produce a function that can replicate the case on
   * another word.
   */
  static String restoreCase(String word, String result) {
    if (word == null || result == null || word == result) return result;
    int len = Math.min(result.length(), word.length());
    if (len == 0) return result;
    char[] chars = result.toCharArray();
    int i = 0;
    for (; i < len; i++) {
      char wc = word.charAt(i);
      if (chars[i] == wc && i != len - 1) continue;
      char uc = Character.toUpperCase(chars[i]);
      char lc = Character.toLowerCase(chars[i]);
      if (wc != lc && wc != uc) break;
      chars[i] = wc;
    }
    if (i < chars.length) {
      char wc = word.charAt(i - 1);
      char uc = Character.toUpperCase(wc);
      char lc = Character.toLowerCase(wc);
      if (uc != lc) {
        for (; i < chars.length; i++) {
          chars[i] = wc == uc ? Character.toUpperCase(chars[i]) : Character.toLowerCase(chars[i]);
        }
      }
    }
    return new String(chars);
  }

  /**
   * Sanitize a word by passing in the word and sanitization rules.
   */
  private String sanitizeWord(String word, List<Pair<Pattern, String>> rules) {
    if (StringUtil.isEmpty(word) || uncountables.contains(word)) return word;

    int len = rules.size();

    while (--len > -1) {
      Pair<Pattern, String> rule = rules.get(len);
      Matcher matcher = rule.first.matcher(word);
      if (matcher.find()) {
        return matcher.replaceFirst(rule.second);
      }
    }
    return null;
  }

  /**
   * Replace a word with the updated word.
   * @return null if no applicable rules found
   */
  private String replaceWord(String word, Map<String, String> replaceMap, Map<String, String> keepMap, List<Pair<Pattern, String>> rules) {
    if (StringUtil.isEmpty(word)) return word;

    // Get the correct token and case restoration functions.
    // Check against the keep object map.
    if (keepMap.containsKey(word)) return word;

    // Check against the replacement map for a direct word replacement.
    String replacement = replaceMap.get(word);
    if (replacement != null) {
      return replacement;
    }

    // Run all the rules against the word.
    return sanitizeWord(word, rules);
  }

  /**
   * Pluralize or singularize a word based on the passed in count.
   */
  public String pluralize(String word, int count, boolean inclusive) {
    String pluralized = count == 1 ? singular(word) : plural(word);

    return (inclusive ? count + " " : "") + notNullize(pluralized, word);
  }

  @Nullable
  public String plural(@Nullable String word) {
    return restoreCase(word, replaceWord(word, irregularSingles, irregularPlurals, pluralRules));
  }

  @Nullable
  public String singular(@Nullable String word) {
    return restoreCase(word, replaceWord(word, irregularPlurals, irregularSingles, singularRules));
  }

  private static Pattern sanitizeRule(String rule) {
    return Pattern.compile(rule.startsWith("/") ? rule.substring(1) : "^" + rule + "$", Pattern.CASE_INSENSITIVE);
  }

  protected void addPluralRule(String rule, String replacement) {
    pluralRules.add(Pair.create(sanitizeRule(rule), replacement));
  }

  protected void addSingularRule(String rule, String replacement) {
    singularRules.add(Pair.create(sanitizeRule(rule), replacement));
  }

  protected void addUncountableRule(String word) {
    if (!word.startsWith("/")) {
      uncountables.add(word);
    }
    else {
      // Set singular and plural references for the word.
      addPluralRule(word, "$0");
      addSingularRule(word, "$0");
    }
  }

  protected void addIrregularRule(String single, String plural) {
    irregularSingles.put(single, plural);
    irregularPlurals.put(plural, single);
  }

  static {
    final Pluralizer pluralizer = new Pluralizer(); 
    
    /*
     * Irregular rules.
     */
    JBIterable.of(new String[][]{
      // Pronouns.
      //{"I", "we"},
      //{"me", "us"},
      //{"he", "they"},
      //{"she", "they"},
      //{"them", "them"},
      //{"myself", "ourselves"},
      //{"yourself", "yourselves"},
      //{"itself", "themselves"},
      //{"herself", "themselves"},
      //{"himself", "themselves"},
      //{"themself", "themselves"},
      //{"is", "are"},
      //{"was", "were"},
      //{"has", "have"},
      {"this", "these"},
      {"that", "those"},
      // Words ending in with a consonant and `o`.
      {"echo", "echoes"},
      {"dingo", "dingoes"},
      {"volcano", "volcanoes"},
      {"tornado", "tornadoes"},
      {"torpedo", "torpedoes"},
      // Ends with `us`.
      {"genus", "genera"},
      {"viscus", "viscera"},
      // Ends with `ma`.
      {"stigma", "stigmata"},
      {"stoma", "stomata"},
      {"dogma", "dogmata"},
      {"lemma", "lemmata"},
      //{"schema", "schemata"},
      {"anathema", "anathemata"},
      // Other irregular rules.
      {"ox", "oxen"},
      {"axe", "axes"},
      {"die", "dice"},
      {"yes", "yeses"},
      {"foot", "feet"},
      {"eave", "eaves"},
      {"goose", "geese"},
      {"tooth", "teeth"},
      {"quiz", "quizzes"},
      {"human", "humans"},
      {"proof", "proofs"},
      {"carve", "carves"},
      {"valve", "valves"},
      {"looey", "looies"},
      {"thief", "thieves"},
      {"groove", "grooves"},
      {"pickaxe", "pickaxes"},
      {"whiskey", "whiskies"}
    }).consumeEach(new Consumer<String[]>() {
      @Override
      public void consume(String[] o) {
        pluralizer.addIrregularRule(o[0], o[1]);
      }
    });

    /*
     * Pluralization rules.
     */
    JBIterable.of(new String[][]{
      {"/s?$", "s"},
      {"/([^aeiou]ese)$", "$1"},
      {"/(ax|test)is$", "$1es"},
      {"/(alias|[^aou]us|t[lm]as|gas|ris)$", "$1es"},
      {"/(e[mn]u)s?$", "$1s"},
      {"/([^l]ias|[aeiou]las|[ejzr]as|[iu]am)$", "$1"},
      {"/(alumn|syllab|octop|vir|radi|nucle|fung|cact|stimul|termin|bacill|foc|uter|loc|strat)(?:us|i)$", "$1i"},
      {"/(alumn|alg|vertebr)(?:a|ae)$", "$1ae"},
      {"/(seraph|cherub)(?:im)?$", "$1im"},
      {"/(her|at|gr)o$", "$1oes"},
      {"/(agend|addend|millenni|medi|dat|extrem|bacteri|desiderat|strat|candelabr|errat|ov|symposi|curricul|automat|quor)(?:a|um)$", "$1a"},
      {"/(apheli|hyperbat|periheli|asyndet|noumen|phenomen|criteri|organ|prolegomen|hedr|automat)(?:a|on)$", "$1a"},
      {"/sis$", "ses"},
      {"/(?:(kni|wi|li)fe|(ar|l|ea|eo|oa|hoo)f)$", "$1$2ves"},
      {"/([^aeiouy]|qu)y$", "$1ies"},
      {"/([^ch][ieo][ln])ey$", "$1ies"},
      {"/(x|ch|ss|sh|zz)$", "$1es"},
      {"/(matr|cod|mur|sil|vert|ind|append)(?:ix|ex)$", "$1ices"},
      {"(m|l)(?:ice|ouse)", "$1ice"},
      {"/(pe)(?:rson|ople)$", "$1ople"},
      {"/(child)(?:ren)?$", "$1ren"},
      {"/eaux$", "$0"},
      {"/m[ae]n$", "men"},
    }).consumeEach(new Consumer<String[]>() {
      @Override
      public void consume(String[] o) {
        pluralizer.addPluralRule(o[0], o[1]);
      }
    });

    /*
     * Singularization rules.
     */
    JBIterable.of(new String[][]{
      {"/(.)s$", "$1"},
      {"/([^aeiou]s)es$", "$1"},
      {"/(wi|kni|(?:after|half|high|low|mid|non|night|[^\\w]|^)li)ves$", "$1fe"},
      {"/(ar|(?:wo|[ae])l|[eo][ao])ves$", "$1f"},
      {"/ies$", "y"},
      {"/\\b([pl]|zomb|(?:neck|cross)?t|coll|faer|food|gen|goon|group|lass|talk|goal|cut)ies$", "$1ie"},
      {"/\\b(mon|smil)ies$", "$1ey"},
      {"(m|l)ice", "$1ouse"},
      {"/(seraph|cherub)im$", "$1"},
      {"/.(x|ch|ss|sh|zz|tto|go|cho|alias|[^aou]us|t[lm]as|gas|(?:her|at|gr)o|ris)(?:es)?$", "$1"},
      {"/(analy|^ba|diagno|parenthe|progno|synop|the|empha|cri)(?:sis|ses)$", "$1sis"},
      {"/(movie|twelve|abuse|e[mn]u)s$", "$1"},
      {"/(test)(?:is|es)$", "$1is"},
      {"/(x|ch|.ss|sh|zz|tto|go|cho|alias|[^aou]us|tlas|gas|(?:her|at|gr)o|ris)(?:es)?$", "$1"},
      {"/(e[mn]u)s?$", "$1"},
      {"/(cookie|movie|twelve)s$", "$1"},
      {"/(cris|test|diagnos)(?:is|es)$", "$1is"},
      {"/(alumn|syllab|octop|vir|radi|nucle|fung|cact|stimul|termin|bacill|foc|uter|loc|strat)(?:us|i)$", "$1us"},
      {"/(agend|addend|millenni|dat|extrem|bacteri|desiderat|strat|candelabr|errat|ov|symposi|curricul|quor)a$", "$1um"},
      {"/(apheli|hyperbat|periheli|asyndet|noumen|phenomen|criteri|organ|prolegomen|hedr|automat)a$", "$1on"},
      {"/(alumn|alg|vertebr)ae$", "$1a"},
      {"/(cod|mur|sil|vert|ind)ices$", "$1ex"},
      {"/(matr|append)ices$", "$1ix"},
      {"/(pe)(rson|ople)$", "$1rson"},
      {"/(child)ren$", "$1"},
      {"/(eau)x?$", "$1"},
      {"/men$", "man"}
    }).consumeEach(new Consumer<String[]>() {
      @Override
      public void consume(String[] o) {
        pluralizer.addSingularRule(o[0], o[1]);
      }
    });
    /*
     * Uncountable rules.
     */
    JBIterable.of(
      // Singular words with no plurals.
      "adulthood",
      "advice",
      "agenda",
      "aid",
      "alcohol",
      "ammo",
      "anime",
      "athletics",
      "audio",
      "bison",
      "blood",
      "bream",
      "buffalo",
      "butter",
      "carp",
      "cash",
      "chassis",
      "chess",
      "clothing",
      "cod",
      "commerce",
      "cooperation",
      "corps",
      "debris",
      "diabetes",
      "digestion",
      "elk",
      "energy",
      "equipment",
      "excretion",
      "expertise",
      "flounder",
      "fun",
      "gallows",
      "garbage",
      "graffiti",
      "headquarters",
      "health",
      "herpes",
      "highjinks",
      "homework",
      "housework",
      "information",
      "jeans",
      "justice",
      "kudos",
      "labour",
      "literature",
      "machinery",
      "mackerel",
      "mail",
      "media",
      "mews",
      "moose",
      "music",
      "news",
      "pike",
      "plankton",
      "pliers",
      "police",
      "pollution",
      "premises",
      "rain",
      "research",
      "rice",
      "salmon",
      "scissors",
      "series",
      "sewage",
      "shambles",
      "shrimp",
      "species",
      "staff",
      "swine",
      "tennis",
      "traffic",
      "transportation",
      "trout",
      "tuna",
      "wealth",
      "welfare",
      "whiting",
      "wildebeest",
      "wildlife",
      "you",
      // Regexes.
      "/[^aeiou]ese$/i", // "chinese", "japanese"
      "/deer$", // "deer", "reindeer"
      "/fish$", // "fish", "blowfish", "angelfish"
      "/measles$",
      "/o[iu]s$", // "carnivorous"
      "/pox$", // "chickpox", "smallpox"
      "/sheep$"
    ).consumeEach(new Consumer<String>() {
      @Override
      public void consume(String o) {
        pluralizer.addUncountableRule(o);
      }
    });

    PLURALIZER = pluralizer;
  }
}
