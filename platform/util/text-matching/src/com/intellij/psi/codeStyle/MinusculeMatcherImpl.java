// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.FList;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search etc.
 *
 * @see NameUtil#buildMatcher(String)
 *
 * @author peter
 */
class MinusculeMatcherImpl extends MinusculeMatcher {
  /** Camel-hump matching is >O(n), so for larger prefixes we fall back to simpler matching to avoid pauses */
  private static final int MAX_CAMEL_HUMP_MATCHING_LENGTH = 100;

  private final char[] myPattern;
  private final String myHardSeparators;
  private final NameUtil.MatchingCaseSensitivity myOptions;
  private final boolean myHasHumps;
  private final boolean myHasSeparators;
  private final boolean myHasDots;
  private final boolean[] isLowerCase;
  private final boolean[] isUpperCase;
  private final boolean[] isWordSeparator;
  private final char[] toUpperCase;
  private final char[] toLowerCase;
  private final char[] myMeaningfulCharacters;
  private final int myMinNameLength;

  /**
   * Constructs a matcher by a given pattern.
   * @param pattern the pattern
   * @param options case sensitivity settings
   * @param hardSeparators A string of characters (empty by default). Lowercase humps don't work for parts separated by any of these characters.
   * Need either an explicit uppercase letter or the same separator character in prefix
   */
  MinusculeMatcherImpl(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, @NotNull String hardSeparators) {
    myOptions = options;
    myPattern = Strings.trimEnd(pattern, "* ").toCharArray();
    myHardSeparators = hardSeparators;
    isLowerCase = new boolean[myPattern.length];
    isUpperCase = new boolean[myPattern.length];
    isWordSeparator = new boolean[myPattern.length];
    toUpperCase = new char[myPattern.length];
    toLowerCase = new char[myPattern.length];
    StringBuilder meaningful = new StringBuilder();
    for (int k = 0; k < myPattern.length; k++) {
      char c = myPattern[k];
      isLowerCase[k] = Character.isLowerCase(c);
      isUpperCase[k] = Character.isUpperCase(c);
      isWordSeparator[k] = isWordSeparator(c);
      toUpperCase[k] = Strings.toUpperCase(c);
      toLowerCase[k] = Strings.toLowerCase(c);
      if (!isWildcard(k)) {
        meaningful.append(toLowerCase[k]);
        meaningful.append(toUpperCase[k]);
      }
    }
    int i = 0;
    while (isWildcard(i)) i++;
    myHasHumps = hasFlag(i + 1, isUpperCase) && hasFlag(i, isLowerCase);
    myHasSeparators = hasFlag(i, isWordSeparator);
    myHasDots = hasDots(i);
    myMeaningfulCharacters = meaningful.toString().toCharArray();
    myMinNameLength = myMeaningfulCharacters.length / 2;
  }

  private static boolean isWordSeparator(char c) {
    return Character.isWhitespace(c) || c == '_' || c == '-' || c == ':' || c == '+' || c == '.';
  }

  private static int nextWord(@NotNull String name, int start) {
    if (start < name.length() && Character.isDigit(name.charAt(start))) {
      return start + 1; //treat each digit as a separate hump
    }
    return NameUtilCore.nextWord(name, start);
  }

  private boolean hasFlag(int start, boolean[] flags) {
    for (int i = start; i < myPattern.length; i++) {
      if (flags[i]) {
        return true;
      }
    }
    return false;
  }

  private boolean hasDots(int start) {
    for (int i = start; i < myPattern.length; i++) {
      if (myPattern[i] == '.') {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static FList<TextRange> prependRange(@NotNull FList<TextRange> ranges, int from, int length) {
    TextRange head = ranges.getHead();
    if (head != null && head.getStartOffset() == from + length) {
      return ranges.getTail().prepend(new TextRange(from, head.getEndOffset()));
    }
    return ranges.prepend(TextRange.from(from, length));
  }

  @Override
  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<? extends TextRange> fragments) {
    if (fragments == null) return Integer.MIN_VALUE;
    if (fragments.isEmpty()) return 0;

    final TextRange first = fragments.getHead();
    boolean startMatch = first.getStartOffset() == 0;
    boolean valuedStartMatch = startMatch && valueStartCaseMatch;

    int matchingCase = 0;
    int p = -1;

    int skippedHumps = 0;
    int nextHumpStart = 0;
    boolean humpStartMatchedUpperCase = false;
    for (TextRange range : fragments) {
      for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
        boolean afterGap = i == range.getStartOffset() && first != range;
        boolean isHumpStart = false;
        while (nextHumpStart <= i) {
          if (nextHumpStart == i) {
            isHumpStart = true;
          }
          else if (afterGap) {
            skippedHumps++;
          }
          nextHumpStart = nextWord(name, nextHumpStart);
        }

        char c = name.charAt(i);
        p = Strings.indexOf(myPattern, c, p + 1, myPattern.length, false);
        if (p < 0) {
          break;
        }

        if (isHumpStart) {
          humpStartMatchedUpperCase = c == myPattern[p] && isUpperCase[p];
        }

        matchingCase += evaluateCaseMatching(valuedStartMatch, p, humpStartMatchedUpperCase, i, afterGap, isHumpStart, c);
      }
    }

    int startIndex = first.getStartOffset();
    boolean afterSeparator = Strings.indexOfAny(name, myHardSeparators, 0, startIndex) >= 0;
    boolean wordStart = startIndex == 0 || NameUtilCore.isWordStart(name, startIndex) && !NameUtilCore.isWordStart(name, startIndex - 1);
    boolean finalMatch = fragments.get(fragments.size() - 1).getEndOffset() == name.length();

    return (wordStart ? 1000 : 0) +
           matchingCase +
           -fragments.size() +
           -skippedHumps * 10 +
           (afterSeparator ? 0 : 2) +
           (startMatch ? 1 : 0) +
           (finalMatch ? 1 : 0);
  }

  private int evaluateCaseMatching(boolean valuedStartMatch,
                                   int patternIndex,
                                   boolean humpStartMatchedUpperCase,
                                   int nameIndex,
                                   boolean afterGap,
                                   boolean isHumpStart,
                                   char nameChar) {
    if (afterGap && isHumpStart && isLowerCase[patternIndex]) {
      return -10; // disprefer when there's a hump but nothing in the pattern indicates the user meant it to be hump
    }
    if (nameChar == myPattern[patternIndex]) {
      if (isUpperCase[patternIndex]) return 50; // strongly prefer user's uppercase matching uppercase: they made an effort to press Shift
      if (nameIndex == 0 && valuedStartMatch) return 150; // the very first letter case distinguishes classes in Java etc
      if (isHumpStart) return 1; // if a lowercase matches lowercase hump start, that also means something
    } else if (isHumpStart) {
      // disfavor hump starts where pattern letter case doesn't match name case
      return -1;
    } else if (isLowerCase[patternIndex] && humpStartMatchedUpperCase) {
      // disfavor lowercase non-humps matching uppercase in the name
      return -1;
    }
    return 0;
  }

  @Override
  @NotNull
  public String getPattern() {
    return new String(myPattern);
  }

  @Override
  @Nullable
  public FList<TextRange> matchingFragments(@NotNull String name) {
    if (name.length() < myMinNameLength) {
      return null;
    }

    if (myPattern.length > MAX_CAMEL_HUMP_MATCHING_LENGTH) {
      return matchBySubstring(name);
    }

    int length = name.length();
    int patternIndex = 0;
    boolean isAscii = true;
    for (int i = 0; i < length; ++i) {
      char c = name.charAt(i);
      if (c >= 128) {
        isAscii = false;
      }
      if (patternIndex < myMeaningfulCharacters.length &&
          (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1])) {
        patternIndex += 2;
      }
    }
    if (patternIndex < myMinNameLength * 2) {
      return null;
    }

    return matchWildcards(name, 0, 0, isAscii);
  }

  @Nullable
  private FList<TextRange> matchBySubstring(@NotNull String name) {
    boolean infix = isPatternChar(0, '*');
    char[] patternWithoutWildChar = filterWildcard(myPattern);
    if (name.length() < patternWithoutWildChar.length) {
      return null;
    }
    if (infix) {
      int index = Strings.indexOfIgnoreCase(name, new CharArrayCharSequence(patternWithoutWildChar, 0, patternWithoutWildChar.length), 0);
      if (index >= 0) {
        return FList.<TextRange>emptyList().prepend(TextRange.from(index, patternWithoutWildChar.length - 1));
      }
      return null;
    }
    if (CharArrayUtil.regionMatches(patternWithoutWildChar, 0, patternWithoutWildChar.length, name)) {
      return FList.<TextRange>emptyList().prepend(new TextRange(0, patternWithoutWildChar.length));
    }
    return null;
  }

  private static char[] filterWildcard(char[] source) {
    char[] buffer = new char[source.length];
    int i = 0;
    for (char c : source) {
      if (c != '*') buffer[i++] = c;
    }

    return Arrays.copyOf(buffer, i);
  }

  /**
   * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
   * and try to {@link #matchFragment} for it.
   */
  @Nullable
  private FList<TextRange> matchWildcards(@NotNull String name,
                                          int patternIndex,
                                          int nameIndex,
                                          boolean isAsciiName) {
    if (nameIndex < 0) {
      return null;
    }
    if (!isWildcard(patternIndex)) {
      if (patternIndex == myPattern.length) {
        return FList.emptyList();
      }
      return matchFragment(name, patternIndex, nameIndex, isAsciiName);
    }

    do {
      patternIndex++;
    } while (isWildcard(patternIndex));

    if (patternIndex == myPattern.length) {
      // the trailing space should match if the pattern ends with the last word part, or only its first hump character
      if (isTrailingSpacePattern() && nameIndex != name.length() && (patternIndex < 2 || !isUpperCaseOrDigit(myPattern[patternIndex - 2]))) {
        int spaceIndex = name.indexOf(' ', nameIndex);
        if (spaceIndex >= 0) {
          return FList.<TextRange>emptyList().prepend(TextRange.from(spaceIndex, 1));
        }
        return null;
      }
      return FList.emptyList();
    }

    return matchSkippingWords(name, patternIndex,
                              findNextPatternCharOccurrence(name, nameIndex, patternIndex, isAsciiName),
                              true, isAsciiName);
  }

  private boolean isTrailingSpacePattern() {
    return isPatternChar(myPattern.length - 1, ' ');
  }

  private static boolean isUpperCaseOrDigit(char p) {
    return Character.isUpperCase(p) || Character.isDigit(p);
  }

  /**
   * Enumerates places in name that could be matched by the pattern at patternIndex position
   * and invokes {@link #matchFragment} at those candidate positions
   */
  @Nullable
  private FList<TextRange> matchSkippingWords(@NotNull String name,
                                              final int patternIndex,
                                              int nameIndex,
                                              boolean allowSpecialChars,
                                              boolean isAsciiName) {
    int maxFoundLength = 0;
    while (nameIndex >= 0) {
      int fragmentLength = seemsLikeFragmentStart(name, patternIndex, nameIndex) ? maxMatchingFragment(name, patternIndex, nameIndex) : 0;

      // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
      // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
      // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
      if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == name.length() && isTrailingSpacePattern()) {
        if (!isMiddleMatch(name, patternIndex, nameIndex)) {
          maxFoundLength = fragmentLength;
        }
        FList<TextRange> ranges = matchInsideFragment(name, patternIndex, nameIndex, isAsciiName, fragmentLength);
        if (ranges != null) {
          return ranges;
        }
      }
      int next = findNextPatternCharOccurrence(name, nameIndex + 1, patternIndex, isAsciiName);
      nameIndex = allowSpecialChars ? next : checkForSpecialChars(name, nameIndex + 1, next, patternIndex);
    }
    return null;
  }

  private int findNextPatternCharOccurrence(@NotNull String name,
                                            int startAt,
                                            int patternIndex,
                                            boolean isAsciiName) {
    return !isPatternChar(patternIndex - 1, '*') && !isWordSeparator[patternIndex]
           ? indexOfWordStart(name, patternIndex, startAt)
           : indexOfIgnoreCase(name, startAt, myPattern[patternIndex], patternIndex, isAsciiName);
  }

  private int checkForSpecialChars(String name, int start, int end, int patternIndex) {
    if (end < 0) return -1;

    // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
    if (!myHasSeparators && !myHasHumps && Strings.containsAnyChar(name, myHardSeparators, start, end)) {
      return -1;
    }
    // if the user has typed a dot, don't skip other dots between humps
    // but one pattern dot may match several name dots
    if (myHasDots && !isPatternChar(patternIndex - 1, '.') && Strings.contains(name, start, end, '.')) {
      return -1;
    }
    return end;
  }

  private boolean seemsLikeFragmentStart(@NotNull String name, int patternIndex, int nextOccurrence) {
    // uppercase should match either uppercase or a word start
    return !isUpperCase[patternIndex] ||
           Character.isUpperCase(name.charAt(nextOccurrence)) ||
           NameUtilCore.isWordStart(name, nextOccurrence) ||
           // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
           !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL;
  }

  private boolean charEquals(char patternChar, int patternIndex, char c, boolean isIgnoreCase) {
    return patternChar == c ||
           isIgnoreCase && (toLowerCase[patternIndex] == c || toUpperCase[patternIndex] == c);
  }

  @Nullable
  private FList<TextRange> matchFragment(@NotNull String name,
                                         int patternIndex,
                                         int nameIndex,
                                         boolean isAsciiName) {
    int fragmentLength = maxMatchingFragment(name, patternIndex, nameIndex);
    return fragmentLength == 0 ? null : matchInsideFragment(name, patternIndex, nameIndex, isAsciiName, fragmentLength);
  }

  private int maxMatchingFragment(@NotNull String name, int patternIndex, int nameIndex) {
    if (!isFirstCharMatching(name, nameIndex, patternIndex)) {
      return 0;
    }

    int i = 1;
    boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    while (nameIndex + i < name.length() && patternIndex + i < myPattern.length) {
      char nameChar = name.charAt(nameIndex + i);
      if (!charEquals(myPattern[patternIndex + i], patternIndex + i, nameChar, ignoreCase)) {
        if (isSkippingDigitBetweenPatternDigits(patternIndex + i, nameChar)) {
          return 0;
        }
        break;
      }
      i++;
    }
    return i;
  }

  private boolean isSkippingDigitBetweenPatternDigits(int patternIndex, char nameChar) {
    return Character.isDigit(myPattern[patternIndex]) && Character.isDigit(myPattern[patternIndex - 1]) && Character.isDigit(nameChar);
  }

  // we've found the longest fragment matching pattern and name
  @Nullable
  private FList<TextRange> matchInsideFragment(@NotNull String name,
                                               int patternIndex,
                                               int nameIndex,
                                               boolean isAsciiName,
                                               int fragmentLength) {
    // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
    int minFragment = isMiddleMatch(name, patternIndex, nameIndex)
                      ? 3 : 1;

    FList<TextRange> camelHumpRanges = improveCamelHumps(name, patternIndex, nameIndex, isAsciiName, fragmentLength, minFragment);
    if (camelHumpRanges != null) {
      return camelHumpRanges;
    }

    return findLongestMatchingPrefix(name, patternIndex, nameIndex, isAsciiName, fragmentLength, minFragment);
  }

  private boolean isMiddleMatch(@NotNull String name, int patternIndex, int nameIndex) {
    return isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
           Character.isLetterOrDigit(name.charAt(nameIndex)) && !NameUtilCore.isWordStart(name, nameIndex);
  }

  @Nullable
  private FList<TextRange> findLongestMatchingPrefix(@NotNull String name,
                                                     int patternIndex,
                                                     int nameIndex,
                                                     boolean isAsciiName,
                                                     int fragmentLength, int minFragment) {
    if (patternIndex + fragmentLength >= myPattern.length) {
      return FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, fragmentLength));
    }

    // try to match the remainder of pattern with the remainder of name
    // it may not succeed with the longest matching fragment, then try shorter matches
    int i = fragmentLength;
    while (i >= minFragment || (i > 0 && isWildcard(patternIndex + i))) {
      FList<TextRange> ranges;
      if (isWildcard(patternIndex + i)) {
        ranges = matchWildcards(name, patternIndex + i, nameIndex + i, isAsciiName);
      }
      else {
        int nextOccurrence = findNextPatternCharOccurrence(name, nameIndex + i + 1, patternIndex + i, isAsciiName);
        nextOccurrence = checkForSpecialChars(name, nameIndex + i, nextOccurrence, patternIndex + i);
        if (nextOccurrence >= 0) {
          ranges = matchSkippingWords(name, patternIndex + i, nextOccurrence, false, isAsciiName);
        } else {
          ranges = null;
        }
      }
      if (ranges != null) {
        return prependRange(ranges, nameIndex, i);
      }
      i--;
    }
    return null;
  }

  /**
   * When pattern is "CU" and the name is "CurrentUser", we already have a prefix "Cu" that matches,
   * but we try to find uppercase "U" later in name for better matching degree
   */
  private FList<TextRange> improveCamelHumps(@NotNull String name,
                                             int patternIndex,
                                             int nameIndex,
                                             boolean isAsciiName,
                                             int maxFragment,
                                             int minFragment) {
    for (int i = minFragment; i < maxFragment; i++) {
      if (isUppercasePatternVsLowercaseNameChar(name, patternIndex + i, nameIndex + i)) {
        FList<TextRange> ranges = findUppercaseMatchFurther(name, patternIndex + i, nameIndex + i, isAsciiName);
        if (ranges != null) {
          return prependRange(ranges, nameIndex, i);
        }
      }
    }
    return null;
  }

  private boolean isUppercasePatternVsLowercaseNameChar(String name, int patternIndex, int nameIndex) {
    return isUpperCase[patternIndex] && myPattern[patternIndex] != name.charAt(nameIndex);
  }

  private FList<TextRange> findUppercaseMatchFurther(String name,
                                                     int patternIndex,
                                                     int nameIndex,
                                                     boolean isAsciiName) {
    int nextWordStart = indexOfWordStart(name, patternIndex, nameIndex);
    return matchWildcards(name, patternIndex, nextWordStart, isAsciiName);
  }

  private boolean isFirstCharMatching(@NotNull String name, int nameIndex, int patternIndex) {
    if (nameIndex >= name.length()) return false;

    boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    char patternChar = myPattern[patternIndex];
    if (!charEquals(patternChar, patternIndex, name.charAt(nameIndex), ignoreCase)) return false;

    if (myOptions == NameUtil.MatchingCaseSensitivity.FIRST_LETTER &&
        (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
        hasCase(patternChar) &&
        Character.isUpperCase(patternChar) != Character.isUpperCase(name.charAt(0))) {
      return false;
    }
    return true;
  }

  private static boolean hasCase(char patternChar) {
    return Character.isUpperCase(patternChar) || Character.isLowerCase(patternChar);
  }

  private boolean isWildcard(int patternIndex) {
    if (patternIndex >= 0 && patternIndex < myPattern.length) {
      char pc = myPattern[patternIndex];
      return pc == ' ' || pc == '*';
    }
    return false;
  }

  private boolean isPatternChar(int patternIndex, char c) {
    return patternIndex >= 0 && patternIndex < myPattern.length && myPattern[patternIndex] == c;
  }

  private int indexOfWordStart(@NotNull String name, int patternIndex, int startFrom) {
    final char p = myPattern[patternIndex];
    if (startFrom >= name.length() ||
        myHasHumps && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])) {
      return -1;
    }
    int nextWordStart = NameUtilCore.isWordStart(name, startFrom) ? startFrom : nextWord(name, startFrom);
    while (true) {
      if (nextWordStart >= name.length()) {
        return -1;
      }
      if (charEquals(p, patternIndex, name.charAt(nextWordStart), true)) {
        return nextWordStart;
      }
      nextWordStart = nextWord(name, nextWordStart);
    }
  }

  private int indexOfIgnoreCase(String name, int fromIndex, char p, int patternIndex, boolean isAsciiName) {
    if (isAsciiName && Strings.isAscii(p)) {
      char pUpper = toUpperCase[patternIndex];
      char pLower = toLowerCase[patternIndex];
      for (int i = fromIndex; i < name.length(); i++) {
        char c = name.charAt(i);
        if (c == p || toUpperAscii(c) == pUpper || toLowerAscii(c) == pLower) {
          return i;
        }
      }
      return -1;
    }
    return Strings.indexOfIgnoreCase(name, p, fromIndex);
  }

  private static char toUpperAscii(char c) {
    if (c >= 'a' && c <= 'z') {
      return (char)(c + ('A' - 'a'));
    }
    return c;
  }

  private static char toLowerAscii(char c) {
    if (c >= 'A' && c <= 'Z') {
      return (char)(c - ('A' - 'a'));
    }
    return c;
  }

  @NonNls
  @Override
  public String toString() {
    return "MinusculeMatcherImpl{myPattern=" + new String(myPattern) + ", myOptions=" + myOptions + '}';
  }

}
