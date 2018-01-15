/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.io.IOUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search etc.
 *
 * @see NameUtil#buildMatcher(String)
 *
 * @author peter
*/
public class MinusculeMatcher implements Matcher {
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
  MinusculeMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, @NotNull String hardSeparators) {
    myOptions = options;
    myPattern = StringUtil.trimEnd(pattern, "* ").toCharArray();
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
      toUpperCase[k] = StringUtil.toUpperCase(c);
      toLowerCase[k] = StringUtil.toLowerCase(c);
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
    return NameUtil.nextWord(name, start);
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

  private static FList<TextRange> prependRange(@NotNull FList<TextRange> ranges, int from, int length) {
    TextRange head = ranges.getHead();
    if (head != null && head.getStartOffset() == from + length) {
      return ranges.getTail().prepend(new TextRange(from, head.getEndOffset()));
    }
    return ranges.prepend(TextRange.from(from, length));
  }

  public int matchingDegree(@NotNull String name) {
    return matchingDegree(name, false);
  }

  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch) {
    return matchingDegree(name, valueStartCaseMatch, matchingFragments(name));
  }

  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<TextRange> fragments) {
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
        p = StringUtil.indexOf(myPattern, c, p + 1, myPattern.length, false);
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
    boolean afterSeparator = StringUtil.indexOfAny(name, myHardSeparators, 0, startIndex) >= 0;
    boolean wordStart = startIndex == 0 || NameUtil.isWordStart(name, startIndex) && !NameUtil.isWordStart(name, startIndex - 1);
    boolean finalMatch = fragments.get(fragments.size() - 1).getEndOffset() == name.length();

    return (wordStart ? 1000 : 0) +
           matchingCase +
           (- fragments.size() - skippedHumps * 10) +
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

  public boolean isStartMatch(@NotNull String name) {
    FList<TextRange> fragments = matchingFragments(name);
    return fragments != null && isStartMatch(fragments);
  }

  public static boolean isStartMatch(@NotNull Iterable<TextRange> fragments) {
    Iterator<TextRange> iterator = fragments.iterator();
    return !iterator.hasNext() || iterator.next().getStartOffset() == 0;
  }

  @Override
  public boolean matches(@NotNull String name) {
    return matchingFragments(name) != null;
  }

  @NotNull
  public String getPattern() {
    return new String(myPattern);
  }

  @Nullable
  public FList<TextRange> matchingFragments(@NotNull String name) {
    if (name.length() < myMinNameLength) {
      return null;
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
      boolean space = isPatternChar(patternIndex - 1, ' ');
      // the trailing space should match if the pattern ends with the last word part, or only its first hump character
      if (space && nameIndex != name.length() && (patternIndex < 2 || !isUpperCaseOrDigit(myPattern[patternIndex - 2]))) {
        int spaceIndex = name.indexOf(' ', nameIndex);
        if (spaceIndex >= 0) {
          return FList.<TextRange>emptyList().prepend(TextRange.from(spaceIndex, 1));
        }
        return null;
      }
      return FList.emptyList();
    }

    FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex, isAsciiName);
    if (ranges != null) {
      return ranges;
    }

    return matchSkippingWords(name, patternIndex, nameIndex, true, isAsciiName);
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
    boolean star = isPatternChar(patternIndex - 1, '*');
    final char p = myPattern[patternIndex];
    while (true) {
      int nextOccurrence = star || isWordSeparator[patternIndex] ?
                           indexOfIgnoreCase(name, nameIndex + 1, p, patternIndex, isAsciiName) :
                           indexOfWordStart(name, patternIndex, nameIndex);
      if (nextOccurrence < 0) {
        return null;
      }
      // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
      if (!allowSpecialChars && !myHasSeparators && !myHasHumps && StringUtil.containsAnyChar(name, myHardSeparators, nameIndex, nextOccurrence)) {
        return null;
      }
      // if the user has typed a dot, don't skip other dots between humps
      // but one pattern dot may match several name dots
      if (!allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.') && StringUtil.contains(name, nameIndex, nextOccurrence, '.')) {
        return null;
      }
      // uppercase should match either uppercase or a word start
      if (!isUpperCase[patternIndex] ||
          Character.isUpperCase(name.charAt(nextOccurrence)) ||
          NameUtil.isWordStart(name, nextOccurrence) ||
          // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
          !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL) {
        FList<TextRange> ranges = matchFragment(name, patternIndex, nextOccurrence, isAsciiName);
        if (ranges != null) {
          return ranges;
        }
      }
      nameIndex = nextOccurrence;
    }
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
    if (!isFirstCharMatching(name, nameIndex, patternIndex)) {
      return null;
    }

    // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
    int minFragment = isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
                      Character.isLetterOrDigit(name.charAt(nameIndex)) && !NameUtil.isWordStart(name, nameIndex)
                      ? 3 : 1;
    int i = 1;
    boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    while (nameIndex + i < name.length() &&
           patternIndex + i < myPattern.length &&
           charEquals(myPattern[patternIndex+i], patternIndex+i, name.charAt(nameIndex + i), ignoreCase)) {
      if (isUppercasePatternVsLowercaseNameChar(name, patternIndex + i, nameIndex + i)) {
        FList<TextRange> ranges = i < minFragment ? null : findUppercaseMatchFurther(name, patternIndex + i, nameIndex + i, isAsciiName);
        if (ranges != null) {
          return prependRange(ranges, nameIndex, i);
        }
        if (shouldProhibitCaseMismatch(name, patternIndex + i, nameIndex + i)) {
          return null;
        }
      }
      i++;
    }

    // we've found the longest fragment matching pattern and name

    if (patternIndex + i >= myPattern.length) {
      return FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i));
    }

    // try to match the remainder of pattern with the remainder of name
    // it may not succeed with the longest matching fragment, then try shorter matches
    while (i >= minFragment || isWildcard(patternIndex + i)) {
      FList<TextRange> ranges = isWildcard(patternIndex + i) ?
                                matchWildcards(name, patternIndex + i, nameIndex + i, isAsciiName) :
                                matchSkippingWords(name, patternIndex + i, nameIndex + i, false, isAsciiName);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, i);
      }
      i--;
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

  private boolean shouldProhibitCaseMismatch(String name, int patternIndex, int nameIndex) {
    // at least three consecutive uppercase letters shouldn't match lowercase
    if (myHasHumps && patternIndex >= 2 && isUpperCase[patternIndex - 1] && isUpperCase[patternIndex - 2]) {
      // but if there's a lowercase after them, it can match (in case shift was released a bit later)
      if (nameIndex + 1 == name.length() ||
          patternIndex + 1 < myPattern.length && !isLowerCase[patternIndex + 1]) {
        return true;
      }
    }
    return false;
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
    int nextWordStart = startFrom;
    while (true) {
      nextWordStart = nextWord(name, nextWordStart);
      if (nextWordStart >= name.length()) {
        return -1;
      }
      if (charEquals(p, patternIndex, name.charAt(nextWordStart), true)) {
        return nextWordStart;
      }
    }
  }

  private int indexOfIgnoreCase(String name, int fromIndex, char p, int patternIndex, boolean isAsciiName) {
    if (isAsciiName && IOUtil.isAscii(p)) {
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
    return StringUtil.indexOfIgnoreCase(name, p, fromIndex);
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
    return "MinusculeMatcher{myPattern=" + new String(myPattern) + ", myOptions=" + myOptions + '}';
  }

}
