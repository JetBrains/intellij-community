// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.Strings;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.text.matching.KeyboardLayoutConverter;
import com.intellij.util.text.matching.MatchingMode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * todo: move to platform module as soon as com.intellij.psi.codeStyle.NameUtil.MatchingCaseSensitivity deleted
 */
public final class NameUtil {
  private static final int MAX_LENGTH = 40;
  //heuristics: 15 can take 10-20 ms in some cases, while 10 works in 1-5 ms
  private static final int TYPO_AWARE_PATTERN_LIMIT = 13;

  private NameUtil() {}

  public static @NotNull List<String> nameToWordsLowerCase(@NotNull String name){
    return NameUtilCore.nameToWordList(name).stream().map(Strings::toLowerCase).toList();
  }

  public static @NotNull String buildRegexp(@NotNull String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower) {
    return buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower, false, false);
  }

  public static @NotNull String buildRegexp(@NotNull String pattern,
                                            int exactPrefixLen,
                                            boolean allowToUpper,
                                            boolean allowToLower,
                                            boolean lowerCaseWords,
                                            boolean forCompletion) {
    final int eol = pattern.indexOf('\n');
    if (eol != -1) {
      pattern = pattern.substring(0, eol);
    }
    if (pattern.length() >= MAX_LENGTH) {
      pattern = pattern.substring(0, MAX_LENGTH);
    }

    final @NonNls StringBuilder buffer = new StringBuilder();
    final boolean endsWithSpace = !forCompletion && Strings.endsWithChar(pattern, ' ');
    if (!forCompletion) {
      pattern = pattern.trim();
    }
    exactPrefixLen = Math.min(exactPrefixLen, pattern.length());
    /*final boolean uppercaseOnly = containsOnlyUppercaseLetters(pattern.substring(exactPrefixLen));
    if (uppercaseOnly) {
      allowToLower = false;
    }*/
    boolean prevIsUppercase = false;
    if (exactPrefixLen > 0) {
      char c = pattern.charAt(exactPrefixLen - 1);
      prevIsUppercase = Character.isUpperCase(c) || Character.isDigit(c);
    }

    for (int i = 0; i != exactPrefixLen; ++i) {
      final char c = pattern.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        buffer.append(c);
      }
      else {
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c);
        //buffer.append(Integer.toHexString(c + 0x20000).substring(2));
      }
    }

    if (exactPrefixLen == 0) {
      buffer.append("_*");  // ignore leading underscores
    }

    boolean firstIdentifierLetter = exactPrefixLen == 0;
    boolean lastIsUppercase = false;
    for (int i = exactPrefixLen; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      lastIsUppercase = false;
      if (Character.isLetterOrDigit(c)) {
        prevIsUppercase = false;

        // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
        if (Character.isUpperCase(c) || Character.isDigit(c)) {
          prevIsUppercase = true;
          lastIsUppercase = true;

          buffer.append('(');

          if (!firstIdentifierLetter) {
            buffer.append("[a-z\\s0-9\\$]*");
          }

          buffer.append(c);
          if (allowToLower) {
            buffer.append('|');
            buffer.append(Character.toLowerCase(c));
          }
          if (!firstIdentifierLetter) {
            buffer.append("|[A-Za-z\\s0-9\\$]*[_-]+[");
            buffer.append(c);
            buffer.append(Character.toLowerCase(c));
            buffer.append("]");
          }
          buffer.append(')');
        }
        else if (Character.isLowerCase(c) && allowToUpper) {
          buffer.append('[');
          buffer.append(c);
          buffer.append(Character.toUpperCase(c));
          buffer.append(']');
          if (lowerCaseWords) {
            buffer.append("([a-z\\s0-9\\$]*[-_]+)?");
          }
        }
        else {
          buffer.append(c);
        }

        firstIdentifierLetter = false;
      }
      else if (c == '*') {
        buffer.append(".*");
        firstIdentifierLetter = true;
      }
      else if (c == '.') {
        if (!firstIdentifierLetter) {
          buffer.append("[a-z\\s0-9\\$]*\\.");
        }
        else {
          buffer.append("\\.");
        }
        firstIdentifierLetter = true;
      }
      else if (c == ' ') {
        buffer.append("([a-z\\s0-9\\$_-]*[\\ _-]+)+");
        firstIdentifierLetter = true;
      }
      else {
        if (c == ':' || prevIsUppercase) {
          buffer.append("[A-Za-z\\s0-9\\$]*");
        }

        firstIdentifierLetter = true;
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c);
        //buffer.append(Integer.toHexString(c + 0x20000).substring(3));
      }
    }

    if (!endsWithSpace) {
      buffer.append(".*");
    }
    else if (lastIsUppercase) {
      buffer.append("[a-z\\s0-9\\$]*");
    }

    //System.out.println("rx = " + buffer.toString());
    return buffer.toString();
  }

  public static @NotNull List<String> getSuggestionsByName(@NotNull String name,
                                                           @NotNull String prefix,
                                                           @NotNull String suffix,
                                                           boolean upperCaseStyle,
                                                           boolean preferLongerNames,
                                                           boolean isArray) {
    ArrayList<String> answer = new ArrayList<>();
    List<@NotNull String> words = NameUtilCore.nameToWordList(name);

    for (int step = 0; step < words.size(); step++) {
      int wordCount = preferLongerNames ? words.size() - step : step + 1;

      String startWord = words.get(words.size() - wordCount);
      char c = startWord.charAt(0);
      if( c == '_' || !Character.isJavaIdentifierStart( c ) )
      {
        continue;
      }

      answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, true) + suffix);
      answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, false) + suffix);
    }
    return answer;
  }

  private static @NotNull String compoundSuggestion(@NotNull String prefix,
                                                    boolean upperCaseStyle,
                                                    List<@NotNull String> words,
                                                    int wordCount,
                                                    @NotNull String startWord,
                                                    char c,
                                                    boolean isArray,
                                                    boolean skip_) {
    StringBuilder buffer = new StringBuilder();

    buffer.append(prefix);

    if (upperCaseStyle) {
      startWord = Strings.toUpperCase(startWord);
    }
    else {
      if (prefix.isEmpty() || Strings.endsWithChar(prefix, '_')) {
        startWord = Strings.toLowerCase(startWord);
      }
      else {
        startWord = Character.toUpperCase(c) + startWord.substring(1);
      }
    }
    buffer.append(startWord);

    for (int i = words.size() - wordCount + 1; i < words.size(); i++) {
      String word = words.get(i);
      String prevWord = words.get(i - 1);
      if (upperCaseStyle) {
        word = Strings.toUpperCase(word);
        if (prevWord.charAt(prevWord.length() - 1) != '_' && word.charAt(0) != '_') {
          word = "_" + word;
        }
      }
      else {
        if (prevWord.charAt(prevWord.length() - 1) == '_') {
          word = Strings.toLowerCase(word);
        }

        if (skip_) {
          if (word.equals("_")) continue;
          if (prevWord.equals("_")) {
            word = Strings.capitalize(word);
          }
        }
      }
      buffer.append(word);
    }

    String suggestion = buffer.toString();
    if (isArray) {
      suggestion = Strings.pluralize(suggestion);
      if (upperCaseStyle) {
        suggestion = Strings.toUpperCase(suggestion);
      }
    }
    return suggestion;
  }

  /**
   * @deprecated use {@link NameUtil#splitNameIntoWordList} (String)} to avoid redundant allocations
   */
  @Deprecated
  public static String @NotNull [] splitNameIntoWords(@NotNull String name) {
    return NameUtilCore.splitNameIntoWords(name);
  }

  @NotNull
  public static List<@NotNull String> splitNameIntoWordList(@NotNull String name) {
    return NameUtilCore.splitNameIntoWordList(name);
  }

  /**
   * @deprecated use {@link NameUtilCore#nameToWordList(String)} to avoid redundant allocations
   */
  @Deprecated
  public static String @NotNull [] nameToWords(@NotNull String name) {
    return NameUtilCore.nameToWords(name);
  }

  @NotNull
  public static List<@NotNull String> nameToWordList(@NotNull String name) {
    return NameUtilCore.nameToWordList(name);
  }

  public static Matcher buildMatcher(@NotNull String pattern,
                                     int exactPrefixLen,
                                     boolean allowToUpper,
                                     boolean allowToLower) {
    MatchingMode matchingMode =
      !allowToLower && !allowToUpper ? MatchingMode.MATCH_CASE
                                     : exactPrefixLen > 0 ? MatchingMode.FIRST_LETTER
                                                          : MatchingMode.IGNORE_CASE;
    return buildMatcher(pattern, matchingMode);
  }

  public static final class MatcherBuilder {
    private final String pattern;
    private String separators = "";
    private MatchingMode matchingMode =
      MatchingMode.IGNORE_CASE;
    private boolean typoTolerant = false;
    private boolean preferStartMatches = false;
    private boolean allOccurrences = false;

    public MatcherBuilder(String pattern) {
      this.pattern = pattern;
    }

    public MatcherBuilder withMatchingMode(MatchingMode matchingMode) {
      this.matchingMode = matchingMode;
      return this;
    }

    /**
     * @deprecated use {@link #withMatchingMode(MatchingMode)}
     */
    @Deprecated
    public MatcherBuilder withCaseSensitivity(MatchingCaseSensitivity caseSensitivity) {
      this.matchingMode = caseSensitivity.matchingMode();
      return this;
    }

    public MatcherBuilder withSeparators(String separators) {
      this.separators = separators;
      return this;
    }

    public MatcherBuilder typoTolerant() {
      this.typoTolerant = pattern.length() <= TYPO_AWARE_PATTERN_LIMIT;
      return this;
    }

    public MatcherBuilder preferringStartMatches() {
      preferStartMatches = true;
      return this;
    }

    public MatcherBuilder allOccurrences() {
      allOccurrences = true;
      return this;
    }

    public MinusculeMatcher build() {
      KeyboardLayoutConverter keyboardLayoutConverter = PlatformKeyboardLayoutConverter.INSTANCE;
      MinusculeMatcher matcher = typoTolerant ? FixingLayoutTypoTolerantMatcher.create(pattern, matchingMode, separators, keyboardLayoutConverter) :
                                 allOccurrences ? AllOccurrencesMatcher.create(pattern, matchingMode, separators, keyboardLayoutConverter) :
                                 new FixingLayoutMatcher(pattern, matchingMode, separators, keyboardLayoutConverter);
      if (preferStartMatches) {
        matcher = new PreferStartMatchMatcherWrapper(matcher);
      }
      matcher = PinyinMatcher.create(pattern, matcher);
      return matcher;
    }
  }

  public static @NotNull MatcherBuilder buildMatcher(@NotNull String pattern) {
    return new MatcherBuilder(pattern);
  }

  public static @NotNull MinusculeMatcher buildMatcher(@NotNull String pattern,
                                                       @NotNull MatchingMode matchingMode) {
    return buildMatcher(pattern).withMatchingMode(matchingMode).build();
  }

  /**
   * @deprecated use {@link #buildMatcher(String, MatchingMode)}
   */
  @Deprecated
  public static @NotNull MinusculeMatcher buildMatcher(@NotNull String pattern, @NotNull MatchingCaseSensitivity options) {
    return buildMatcher(pattern, options.matchingMode());
  }

  public static MinusculeMatcher buildMatcherWithFallback(@NotNull String pattern,
                                                          @NotNull String fallbackPattern,
                                                          @NotNull MatchingMode matchingMode) {
    return pattern.equals(fallbackPattern)
           ? buildMatcher(pattern, matchingMode)
           : new MatcherWithFallback(buildMatcher(pattern, matchingMode), buildMatcher(fallbackPattern, matchingMode));
  }

  public static @NotNull String capitalizeAndUnderscore(@NotNull String name) {
    return splitWords(name, '_', Strings::toUpperCase);
  }

  public static @NotNull String splitWords(@NotNull String text, char separator, @NotNull Function<? super String, String> transformWord) {
    final List<@NotNull String> words = NameUtilCore.nameToWordList(text);
    boolean insertSeparator = false;
    final StringBuilder buf = new StringBuilder();
    for (String word : words) {
      if (!Character.isLetterOrDigit(word.charAt(0))) {
        buf.append(separator);
        insertSeparator = false;
        continue;
      }
      if (insertSeparator) {
        buf.append(separator);
      } else {
        insertSeparator = true;
      }
      buf.append(transformWord.apply(word));
    }
    return buf.toString();

  }

  /**
   * @deprecated use {@link MatchingMode} instead
   */
  @Deprecated
  public enum MatchingCaseSensitivity {
    NONE, FIRST_LETTER, ALL;

    @NotNull MatchingMode matchingMode() {
      return switch (this) {
        case NONE -> MatchingMode.IGNORE_CASE;
        case FIRST_LETTER -> MatchingMode.FIRST_LETTER;
        case ALL -> MatchingMode.MATCH_CASE;
      };
    }
  }
}
