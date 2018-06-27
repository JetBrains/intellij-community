// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  private static FList<Range> prependRange(@NotNull FList<Range> ranges, @NotNull Range range) {
    Range head = ranges.getHead();
    if (head != null && head.getStartOffset() == range.getEndOffset()) {
      return ranges.getTail().prepend(new Range(range.getStartOffset(), head.getEndOffset(), range.getErrorCount() + head.getErrorCount()));
    }
    return ranges.prepend(range);
  }

  public int matchingDegree(@NotNull String name) {
    return matchingDegree(name, false);
  }

  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch) {
    return matchingDegree(name, valueStartCaseMatch, matchingFragments(name));
  }

  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<Range> fragments) {
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

  public boolean isStartMatch(@NotNull String name) {
    FList<Range> fragments = matchingFragments(name);
    return fragments != null && isStartMatch(fragments);
  }

  public static boolean isStartMatch(@NotNull Iterable<Range> fragments) {
    Iterator<Range> iterator = fragments.iterator();
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
  public FList<Range> matchingFragments(@NotNull String name) {
    return new Session(name).matchingFragments();
  }

  private class Session {
    @NotNull private final String myName;
    private final boolean isAsciiName;

    public Session(@NotNull String name) {
      myName = name;

      int length = name.length();
      boolean isAscii = true;
      for (int i = 0; i < length; ++i) {
        char c = name.charAt(i);
        if (c >= 128) {
          isAscii = false;
        }
      }

      isAsciiName = isAscii;
    }

    @Nullable
    public FList<Range> matchingFragments() {
      if (myName.length() < myMinNameLength) {
        return null;
      }

      int length = myName.length();
      int patternIndex = 0;
      for (int i = 0; i < length; ++i) {
        char c = myName.charAt(i);
        if (patternIndex < myMeaningfulCharacters.length &&
            (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1])) {
          patternIndex += 2;
        }
      }
      if (patternIndex < myMinNameLength * 2) {
        return null;
      }

      return matchWildcards(0, 0);
    }

    /**
     * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
     * and try to {@link #matchFragment} for it.
     */
    @Nullable
    private FList<Range> matchWildcards(int patternIndex,
                                        int nameIndex) {
      if (nameIndex < 0) {
        return null;
      }
      if (!isWildcard(patternIndex)) {
        if (patternIndex == myPattern.length) {
          return FList.emptyList();
        }
        return matchFragment(patternIndex, nameIndex);
      }

      do {
        patternIndex++;
      } while (isWildcard(patternIndex));

      if (patternIndex == myPattern.length) {
        // the trailing space should match if the pattern ends with the last word part, or only its first hump character
        if (isTrailingSpacePattern() && nameIndex != myName.length() && (patternIndex < 2 || !isUpperCaseOrDigit(myPattern[patternIndex - 2]))) {
          int spaceIndex = myName.indexOf(' ', nameIndex);
          if (spaceIndex >= 0) {
            return FList.<Range>emptyList().prepend(new Range(spaceIndex, spaceIndex + 1, 0)); //todo looks like it's okay here
          }
          return null;
        }
        return FList.emptyList();
      }

      FList<Range> ranges = matchFragment(patternIndex, nameIndex);
      if (ranges != null) {
        return ranges;
      }

      return matchSkippingWords(patternIndex, nameIndex, true);
    }

    private boolean isTrailingSpacePattern() {
      return isPatternChar(myPattern.length - 1, ' ');
    }

    private boolean isUpperCaseOrDigit(char p) {
      return Character.isUpperCase(p) || Character.isDigit(p);
    }

    /**
     * Enumerates places in name that could be matched by the pattern at patternIndex position
     * and invokes {@link #matchFragment} at those candidate positions
     */
    @Nullable
    private FList<Range> matchSkippingWords(int patternIndex,
                                            int nameIndex,
                                            boolean allowSpecialChars) {
      boolean wordStartsOnly = !isPatternChar(patternIndex - 1, '*') && !isWordSeparator[patternIndex];

      int maxFoundLength = 0;
      while (true) {
        nameIndex = findNextPatternCharOccurrence(nameIndex, patternIndex, allowSpecialChars, wordStartsOnly);
        if (nameIndex < 0) {
          return null;
        }
        int fragmentLength = seemsLikeFragmentStart(patternIndex, nameIndex) ? maxMatchingFragment(patternIndex, nameIndex) : 0;

        // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
        // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
        // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
        if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == myName.length() && isTrailingSpacePattern()) {
          if (!isMiddleMatch(patternIndex, nameIndex)) {
            maxFoundLength = fragmentLength;
          }
          FList<Range> ranges = matchInsideFragment(patternIndex, nameIndex, fragmentLength);
          if (ranges != null) {
            return ranges;
          }
        }
      }
    }

    private int findNextPatternCharOccurrence(int startAt,
                                              int patternIndex,
                                              boolean allowSpecialChars, boolean wordStartsOnly) {
      int next = wordStartsOnly
                 ? indexOfWordStart(patternIndex, startAt)
                 : indexOfIgnoreCase(startAt + 1, myPattern[patternIndex], patternIndex);

      // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
      if (!allowSpecialChars && !myHasSeparators && !myHasHumps && StringUtil.containsAnyChar(myName, myHardSeparators, startAt, next)) {
        return -1;
      }
      // if the user has typed a dot, don't skip other dots between humps
      // but one pattern dot may match several name dots
      if (!allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.') && StringUtil.contains(myName, startAt, next, '.')) {
        return -1;
      }

      return next;
    }

    private boolean seemsLikeFragmentStart(int patternIndex, int nextOccurrence) {
      // uppercase should match either uppercase or a word start
      return !isUpperCase[patternIndex] ||
             Character.isUpperCase(myName.charAt(nextOccurrence)) ||
             NameUtil.isWordStart(myName, nextOccurrence) ||
             // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
             !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    }

    private boolean charEquals(char patternChar, int patternIndex, char c, boolean isIgnoreCase) {
      return patternChar == c ||
             isIgnoreCase && (toLowerCase[patternIndex] == c || toUpperCase[patternIndex] == c);
    }

    @Nullable
    private FList<Range> matchFragment(int patternIndex,
                                       int nameIndex) {
      int fragmentLength = maxMatchingFragment(patternIndex, nameIndex);
      return fragmentLength == 0 ? null : matchInsideFragment(patternIndex, nameIndex, fragmentLength);
    }

    private int maxMatchingFragment(int patternIndex, int nameIndex) {
      if (!isFirstCharMatching(nameIndex, patternIndex)) {
        return 0;
      }

      int i = 1;
      boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
      while (nameIndex + i < myName.length() && patternIndex + i < myPattern.length) {
        if (!charEquals(myPattern[patternIndex + i], patternIndex + i, myName.charAt(nameIndex + i), ignoreCase)) {
          if (Character.isDigit(myPattern[patternIndex + i]) && Character.isDigit(myPattern[patternIndex + i - 1])) {
            return 0;
          }
          break;
        }
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i) &&
            shouldProhibitCaseMismatch(patternIndex + i, nameIndex + i)) {
          break;
        }
        i++;
      }
      return i;
    }

    // we've found the longest fragment matching pattern and name
    @Nullable
    private FList<Range> matchInsideFragment(int patternIndex,
                                             int nameIndex,
                                             int fragmentLength) {
      // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
      int minFragment = isMiddleMatch(patternIndex, nameIndex)
                        ? 3 : 1;

      FList<Range> camelHumpRanges = improveCamelHumps(patternIndex, nameIndex, fragmentLength, minFragment);
      if (camelHumpRanges != null) {
        return camelHumpRanges;
      }

      return findLongestMatchingPrefix(patternIndex, nameIndex, fragmentLength, minFragment);
    }

    private boolean isMiddleMatch(int patternIndex, int nameIndex) {
      return isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
             Character.isLetterOrDigit(myName.charAt(nameIndex)) && !NameUtil.isWordStart(myName, nameIndex);
    }

    @Nullable
    private FList<Range> findLongestMatchingPrefix(int patternIndex,
                                                   int nameIndex,
                                                   int fragmentLength, int minFragment) {
      if (patternIndex + fragmentLength >= myPattern.length) {
        return FList.<Range>emptyList().prepend(new Range(nameIndex, nameIndex + fragmentLength, 0)); //todo we need to add errors here!!!
      }

      // try to match the remainder of pattern with the remainder of name
      // it may not succeed with the longest matching fragment, then try shorter matches
      for (int i = fragmentLength; i >= minFragment || isWildcard(patternIndex + i); i--) {
        FList<Range> ranges = isWildcard(patternIndex + i) ?
                              matchWildcards(patternIndex + i, nameIndex + i) :
                              matchSkippingWords(patternIndex + i, nameIndex + i, false);
        if (ranges != null) {
          return prependRange(ranges, new Range(nameIndex, nameIndex + i, 0)); //todo check errors???
        }
      }
      return null;
    }

    /**
     * When pattern is "CU" and the name is "CurrentUser", we already have a prefix "Cu" that matches,
     * but we try to find uppercase "U" later in name for better matching degree
     */
    @Nullable
    private FList<Range> improveCamelHumps(int patternIndex,
                                           int nameIndex,
                                           int maxFragment,
                                           int minFragment) {
      for (int i = minFragment; i < maxFragment; i++) {
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i)) {
          FList<Range> ranges = findUppercaseMatchFurther(patternIndex + i, nameIndex + i);
          if (ranges != null) {
            return prependRange(ranges, new Range(nameIndex, nameIndex + i, 0)); //todo we need to add error info here!!!
          }
        }
      }
      return null;
    }

    private boolean isUppercasePatternVsLowercaseNameChar(int patternIndex, int nameIndex) {
      return isUpperCase[patternIndex] && myPattern[patternIndex] != myName.charAt(nameIndex);
    }

    @Nullable
    private FList<Range> findUppercaseMatchFurther(int patternIndex,
                                                   int nameIndex) {
      int nextWordStart = indexOfWordStart(patternIndex, nameIndex);
      return matchWildcards(patternIndex, nextWordStart);
    }

    private boolean shouldProhibitCaseMismatch(int patternIndex, int nameIndex) {
      // at least three consecutive uppercase letters shouldn't match lowercase
      if (myHasHumps && patternIndex >= 2 && isUpperCase[patternIndex - 1] && isUpperCase[patternIndex - 2]) {
        // but if there's a lowercase after them, it can match (in case shift was released a bit later)
        if (nameIndex + 1 == myName.length() ||
            patternIndex + 1 < myPattern.length && !isLowerCase[patternIndex + 1]) {
          return true;
        }
      }
      return false;
    }

    private boolean isFirstCharMatching(int nameIndex, int patternIndex) {
      if (nameIndex >= myName.length()) return false;

      boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
      char patternChar = myPattern[patternIndex];
      if (!charEquals(patternChar, patternIndex, myName.charAt(nameIndex), ignoreCase)) return false;

      if (myOptions == NameUtil.MatchingCaseSensitivity.FIRST_LETTER &&
          (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
          hasCase(patternChar) &&
          Character.isUpperCase(patternChar) != Character.isUpperCase(myName.charAt(0))) {
        return false;
      }
      return true;
    }

    private boolean hasCase(char patternChar) {
      return Character.isUpperCase(patternChar) || Character.isLowerCase(patternChar);
    }

    private boolean isPatternChar(int patternIndex, char c) {
      return patternIndex >= 0 && patternIndex < myPattern.length && myPattern[patternIndex] == c;
    }

    private int indexOfWordStart(int patternIndex, int startFrom) {
      final char p = myPattern[patternIndex];
      if (startFrom >= myName.length() ||
          myHasHumps && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])) {
        return -1;
      }
      int nextWordStart = startFrom;
      while (true) {
        nextWordStart = nextWord(myName, nextWordStart);
        if (nextWordStart >= myName.length()) {
          return -1;
        }
        if (charEquals(p, patternIndex, myName.charAt(nextWordStart), true)) {
          return nextWordStart;
        }
      }
    }

    private int indexOfIgnoreCase(int fromIndex, char p, int patternIndex) {
      if (isAsciiName && IOUtil.isAscii(p)) {
        char pUpper = toUpperCase[patternIndex];
        char pLower = toLowerCase[patternIndex];
        for (int i = fromIndex; i < myName.length(); i++) {
          char c = myName.charAt(i);
          if (c == p || toUpperAscii(c) == pUpper || toLowerAscii(c) == pLower) {
            return i;
          }
        }
        return -1;
      }
      return StringUtil.indexOfIgnoreCase(myName, p, fromIndex);
    }
  }

  private boolean isWildcard(int patternIndex) {
    if (patternIndex >= 0 && patternIndex < myPattern.length) {
      char pc = myPattern[patternIndex];
      return pc == ' ' || pc == '*';
    }
    return false;
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
