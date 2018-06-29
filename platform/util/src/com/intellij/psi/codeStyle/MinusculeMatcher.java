// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FList;
import com.intellij.util.io.IOUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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

  private final TypoService myTypoService = TypoService.getInstance();

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

    int errors = 0;
    int matchingCase = 0;
    int p = -1;

    int skippedHumps = 0;
    int nextHumpStart = 0;
    boolean humpStartMatchedUpperCase = false;
    for (Range range : fragments) {
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

      errors += 2000.0 * Math.pow(1.0 * range.getErrorCount() / range.getLength(), 2);
    }

    int startIndex = first.getStartOffset();
    boolean afterSeparator = StringUtil.indexOfAny(name, myHardSeparators, 0, startIndex) >= 0;
    boolean wordStart = startIndex == 0 || NameUtil.isWordStart(name, startIndex) && !NameUtil.isWordStart(name, startIndex - 1);
    boolean finalMatch = fragments.get(fragments.size() - 1).getEndOffset() == name.length();

    return (wordStart ? 1000 : 0) +
           matchingCase +
           -fragments.size() +
           -skippedHumps * 10 +
           -errors +
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
    FList<Range> ranges = new Session(name, false).matchingFragments();
    if (ranges != null) return ranges;
    return new Session(name, true).matchingFragments();
  }

  private class Session {
    @NotNull private final String myName;
    private final boolean isAsciiName;

    private final boolean myTypoAware;
    private final boolean myAllowTypos;

    public Session(@NotNull String name, boolean typoAware) {
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
      myTypoAware = typoAware;
      myAllowTypos = typoAware && isAscii;
    }

    private char charAt(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? errorState.getPattern(myPattern)[i] : myPattern[i];
    }

    private char toLowerCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? toLowerAscii(errorState.getPattern(myPattern)[i]) : toLowerCase[i];
    }

    private char toUpperCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? toUpperAscii(errorState.getPattern(myPattern)[i]) : toUpperCase[i];
    }

    private boolean isLowerCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? isLowerAscii(errorState.getPattern(myPattern)[i]) : isLowerCase[i];
    }

    private boolean isUpperCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? isUpperAscii(errorState.getPattern(myPattern)[i]) : isUpperCase[i];
    }

    private boolean isWordSeparator(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? MinusculeMatcher.isWordSeparator(errorState.getPattern(myPattern)[i]) : isWordSeparator[i];
    }

    private int patternLength(@NotNull ErrorState errorState) {
      return myPattern.length;
    }

    @Nullable
    public FList<Range> matchingFragments() {
      if (myName.length() < myMinNameLength) {
        return null;
      }

      //we're in typo mode, but non-ascii symbols are used. so aborting
      if (myTypoAware && !isAsciiName) return null;

      if (!myTypoAware) {
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
      }

      return matchWildcards(0, 0, new ErrorState());
    }

    /**
     * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
     * and try to {@link #matchFragment} for it.
     */
    @Nullable
    private FList<Range> matchWildcards(int patternIndex,
                                        int nameIndex,
                                        @NotNull ErrorState errorState) {
      if (nameIndex < 0) {
        return null;
      }
      if (!isWildcard(patternIndex)) {
        if (patternIndex == patternLength(errorState)) {
          return FList.emptyList();
        }
        return matchFragment(patternIndex, nameIndex, errorState);
      }

      do {
        patternIndex++;
      } while (isWildcard(patternIndex));

      if (patternIndex == patternLength(errorState)) {
        // the trailing space should match if the pattern ends with the last word part, or only its first hump character
        if (isTrailingSpacePattern(errorState) && nameIndex != myName.length() && (patternIndex < 2 || !isUpperCaseOrDigit(charAt(
          patternIndex - 2, errorState)))) {
          int spaceIndex = myName.indexOf(' ', nameIndex);
          if (spaceIndex >= 0) {
            return FList.<Range>emptyList().prepend(new Range(spaceIndex, spaceIndex + 1, 0));
          }
          return null;
        }
        return FList.emptyList();
      }

      FList<Range> ranges = matchFragment(patternIndex, nameIndex, errorState);
      if (ranges != null) {
        return ranges;
      }

      return matchSkippingWords(patternIndex, nameIndex, true, errorState);
    }

    private boolean isTrailingSpacePattern(@NotNull ErrorState errorState) {
      return isPatternChar(patternLength(errorState) - 1, ' ', errorState);
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
                                            boolean allowSpecialChars,
                                            @NotNull ErrorState errorState) {
      boolean wordStartsOnly = !isPatternChar(patternIndex - 1, '*', errorState) && !isWordSeparator(patternIndex, errorState);

      int maxFoundLength = 0;
      while (true) {
        nameIndex = findNextPatternCharOccurrence(nameIndex, patternIndex, allowSpecialChars, wordStartsOnly, errorState);
        if (nameIndex < 0) {
          return null;
        }
        Fragment fragment = seemsLikeFragmentStart(patternIndex, nameIndex, errorState) ? maxMatchingFragment(patternIndex, nameIndex, errorState) : null;
        if (fragment == null) continue;

        // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
        // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
        // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
        int fragmentLength = fragment.getLength();
        if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == myName.length() && isTrailingSpacePattern(errorState)) {
          if (!isMiddleMatch(patternIndex, nameIndex, errorState)) {
            maxFoundLength = fragmentLength;
          }
          FList<Range> ranges = matchInsideFragment(patternIndex, nameIndex, fragment);
          if (ranges != null) {
            return ranges;
          }
        }
      }
    }

    private int findNextPatternCharOccurrence(int startAt,
                                              int patternIndex,
                                              boolean allowSpecialChars,
                                              boolean wordStartsOnly,
                                              @NotNull ErrorState errorState) {
      int next = wordStartsOnly
                 ? indexOfWordStart(patternIndex, startAt, errorState)
                 : indexOfIgnoreCase(startAt + 1, patternIndex, errorState);

      // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
      if (!allowSpecialChars && !myHasSeparators && !myHasHumps && StringUtil.containsAnyChar(myName, myHardSeparators, startAt, next)) {
        return -1;
      }
      // if the user has typed a dot, don't skip other dots between humps
      // but one pattern dot may match several name dots
      if (!allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.', errorState) && StringUtil.contains(myName, startAt, next, '.')) {
        return -1;
      }

      return next;
    }

    private boolean seemsLikeFragmentStart(int patternIndex, int nextOccurrence, @NotNull ErrorState errorState) {
      // uppercase should match either uppercase or a word start
      return !isUpperCase(patternIndex, errorState) ||
             Character.isUpperCase(myName.charAt(nextOccurrence)) ||
             NameUtil.isWordStart(myName, nextOccurrence) ||
             // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
             !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    }

    private boolean charEquals(int patternIndex, int nameIndex, boolean isIgnoreCase, boolean allowTypos, @NotNull ErrorState errorState) {
      char patternChar = charAt(patternIndex, errorState);
      char nameChar = myName.charAt(nameIndex);

      if (patternChar == nameChar ||
          isIgnoreCase && (toLowerCase(patternIndex, errorState) == nameChar || toUpperCase(patternIndex, errorState) == nameChar)) {
        return true;
      }

      if (!myAllowTypos || !allowTypos) return false;

      if (errorState.countErrors(0, patternIndex) > 1) return false;
      Error prevError = errorState.getError(patternIndex - 1);
      if (prevError == SwapError.instance) {
        return false;
      }

      char leftMiss = myTypoService.leftMiss(patternChar);
      if (leftMiss != 0) {
        if (leftMiss == nameChar ||
            isIgnoreCase && (toLowerAscii(leftMiss) == nameChar || toUpperAscii(leftMiss) == nameChar)) {
          errorState.addError(patternIndex, new TypoError(leftMiss));
          return true;
        }
      }

      char rightMiss = myTypoService.rightMiss(patternChar);
      if (rightMiss != 0) {
        if (rightMiss == nameChar ||
            isIgnoreCase && (toLowerAscii(rightMiss) == nameChar || toUpperAscii(rightMiss) == nameChar)) {
          errorState.addError(patternIndex, new TypoError(rightMiss));
          return true;
        }
      }

      if (patternLength(errorState) > patternIndex + 1 && myName.length() > nameIndex + 1) {
        char nextNameChar = myName.charAt(nameIndex + 1);
        char nextPatternChar = charAt(patternIndex + 1, errorState);

        if ((patternChar == nextNameChar || isIgnoreCase && (toLowerCase(patternIndex, errorState) == nextNameChar || toUpperCase(patternIndex, errorState) == nextNameChar)) &&
            (nextPatternChar == nameChar || isIgnoreCase && (toLowerCase(patternIndex + 1, errorState) == nameChar || toUpperCase(patternIndex + 1, errorState) == nameChar))) {
          errorState.addError(patternIndex, SwapError.instance);
          return true;
        }
      }

      return false;
    }

    @Nullable
    private FList<Range> matchFragment(int patternIndex,
                                       int nameIndex,
                                       @NotNull ErrorState errorState) {
      Fragment fragment = maxMatchingFragment(patternIndex, nameIndex, errorState);
      return fragment == null ? null : matchInsideFragment(patternIndex, nameIndex, fragment);
    }

    @Nullable
    private Fragment maxMatchingFragment(int patternIndex, int nameIndex, @NotNull ErrorState baseErrorState) {
      ErrorState errorState = baseErrorState.deriveFrom(patternIndex);

      if (!isFirstCharMatching(nameIndex, patternIndex, errorState)) {
        return null;
      }

      int i = 1;
      boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
      while (nameIndex + i < myName.length() && patternIndex + i < patternLength(errorState)) {
        if (!charEquals(patternIndex + i, nameIndex + i, ignoreCase, true, errorState)) {
          if (Character.isDigit(charAt(patternIndex + i, errorState)) && Character.isDigit(charAt(patternIndex + i - 1, errorState))) {
            return null;
          }
          break;
        }
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i, errorState) &&
            shouldProhibitCaseMismatch(patternIndex + i, nameIndex + i, errorState)) {
          break;
        }
        i++;
      }
      return new Fragment(i, errorState);
    }

    // we've found the longest fragment matching pattern and name
    @Nullable
    private FList<Range> matchInsideFragment(int patternIndex,
                                             int nameIndex,
                                             @NotNull Fragment fragment) {
      // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
      int minFragment = isMiddleMatch(patternIndex, nameIndex, fragment.getErrorState())
                        ? 3 : 1;

      FList<Range> camelHumpRanges = improveCamelHumps(patternIndex, nameIndex, fragment.getLength(), minFragment, fragment.getErrorState());
      if (camelHumpRanges != null) {
        return camelHumpRanges;
      }

      return findLongestMatchingPrefix(patternIndex, nameIndex, fragment.getLength(), minFragment, fragment.getErrorState());
    }

    private boolean isMiddleMatch(int patternIndex, int nameIndex, @NotNull ErrorState errorState) {
      return isPatternChar(patternIndex - 1, '*', errorState) && !isWildcard(patternIndex + 1) &&
             Character.isLetterOrDigit(myName.charAt(nameIndex)) && !NameUtil.isWordStart(myName, nameIndex);
    }

    @Nullable
    private FList<Range> findLongestMatchingPrefix(int patternIndex,
                                                   int nameIndex,
                                                   int fragmentLength, int minFragment,
                                                   @NotNull ErrorState errorState) {
      if (patternIndex + fragmentLength >= patternLength(errorState)) {
        int errors = errorState.countErrors(patternIndex, patternIndex + fragmentLength);
        if (errors == fragmentLength) return null;
        return FList.<Range>emptyList().prepend(new Range(nameIndex, nameIndex + fragmentLength, errors));
      }

      // try to match the remainder of pattern with the remainder of name
      // it may not succeed with the longest matching fragment, then try shorter matches
      for (int i = fragmentLength; i >= minFragment || isWildcard(patternIndex + i); i--) {
        ErrorState derivedErrorState = errorState.deriveFrom(patternIndex + i);
        FList<Range> ranges = isWildcard(patternIndex + i) ?
                              matchWildcards(patternIndex + i, nameIndex + i, derivedErrorState) :
                              matchSkippingWords(patternIndex + i, nameIndex + i, false, derivedErrorState);
        if (ranges != null) {
          int errors = errorState.countErrors(patternIndex, patternIndex + i);
          if (errors == i) return null;
          return prependRange(ranges, new Range(nameIndex, nameIndex + i, errors));
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
                                           int minFragment,
                                           @NotNull ErrorState errorState) {
      for (int i = minFragment; i < maxFragment; i++) {
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i, errorState)) {
          FList<Range> ranges = findUppercaseMatchFurther(patternIndex + i, nameIndex + i, errorState.deriveFrom(patternIndex + i));
          if (ranges != null) {
            int errors = errorState.countErrors(patternIndex, patternIndex + i);
            if (errors == i) return null;
            return prependRange(ranges, new Range(nameIndex, nameIndex + i, errors));
          }
        }
      }
      return null;
    }

    private boolean isUppercasePatternVsLowercaseNameChar(int patternIndex, int nameIndex, @NotNull ErrorState errorState) {
      return isUpperCase(patternIndex, errorState) && !charEquals(patternIndex, nameIndex, false, false, errorState);
    }

    @Nullable
    private FList<Range> findUppercaseMatchFurther(int patternIndex,
                                                   int nameIndex,
                                                   @NotNull ErrorState errorState) {
      int nextWordStart = indexOfWordStart(patternIndex, nameIndex, errorState);
      return matchWildcards(patternIndex, nextWordStart, errorState.deriveFrom(patternIndex));
    }

    private boolean shouldProhibitCaseMismatch(int patternIndex, int nameIndex, @NotNull ErrorState errorState) {
      // at least three consecutive uppercase letters shouldn't match lowercase
      if (myHasHumps && patternIndex >= 2 && isUpperCase(patternIndex - 1, errorState) && isUpperCase(patternIndex - 2, errorState)) {
        // but if there's a lowercase after them, it can match (in case shift was released a bit later)
        if (nameIndex + 1 == myName.length() ||
            patternIndex + 1 < patternLength(errorState) && !isLowerCase(patternIndex + 1, errorState)) {
          return true;
        }
      }
      return false;
    }

    private boolean isFirstCharMatching(int nameIndex, int patternIndex, @NotNull ErrorState errorState) {
      if (nameIndex >= myName.length()) return false;

      boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
      if (!charEquals(patternIndex, nameIndex, ignoreCase, true, errorState)) return false;

      char patternChar = charAt(patternIndex, errorState);

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

    private boolean isPatternChar(int patternIndex, char c, @NotNull ErrorState errorState) {
      return patternIndex >= 0 && patternIndex < patternLength(errorState) && charAt(patternIndex, errorState) == c;
    }

    private int indexOfWordStart(int patternIndex, int startFrom, @NotNull ErrorState errorState) {
      if (startFrom >= myName.length() ||
          myHasHumps && isLowerCase(patternIndex, errorState) && !(patternIndex > 0 && isWordSeparator(patternIndex - 1, errorState))) {
        return -1;
      }
      int nextWordStart = startFrom;
      while (true) {
        nextWordStart = nextWord(myName, nextWordStart);
        if (nextWordStart >= myName.length()) {
          return -1;
        }
        if (charEquals(patternIndex, nextWordStart, true, true, errorState)) {
          return nextWordStart;
        }
      }
    }

    private int indexOfIgnoreCase(int fromIndex, int patternIndex, @NotNull ErrorState errorState) {
      char p = charAt(patternIndex, errorState);
      if (isAsciiName && IOUtil.isAscii(p)) {
        int i = indexIgnoringCaseAscii(fromIndex, patternIndex, p);
        if (i != -1) return i;

        if (myAllowTypos) {
          int leftMiss = indexIgnoringCaseAscii(fromIndex, patternIndex, myTypoService.leftMiss(p));
          if (leftMiss != -1) return leftMiss;

          int rightMiss = indexIgnoringCaseAscii(fromIndex, patternIndex, myTypoService.rightMiss(p));
          if (rightMiss != -1) return rightMiss;
        }

        return -1;
      }
      return StringUtil.indexOfIgnoreCase(myName, p, fromIndex);
    }

    @Nullable
    private int indexIgnoringCaseAscii(int fromIndex, int patternIndex, char p) {
      char pUpper = toUpperAscii(p);
      char pLower = toLowerAscii(p);
      for (int i = fromIndex; i < myName.length(); i++) {
        char c = myName.charAt(i);
        if (c == p || toUpperAscii(c) == pUpper || toLowerAscii(c) == pLower) {
          return i;
        }
      }
      return -1;
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

  private static boolean isUpperAscii(char c) {
    return 'A' <= c && c <= 'Z';
  }

  private static boolean isLowerAscii(char c) {
    return 'a' <= c && c <= 'z';
  }

  @NonNls
  @Override
  public String toString() {
    return "MinusculeMatcher{myPattern=" + new String(myPattern) + ", myOptions=" + myOptions + '}';
  }


  private static class ErrorState {
    @Nullable private final ErrorState myBase;
    private final int myDeriveIndex;

    private List<Pair<Integer, Error>> myErrors;

    private char[] myPattern;

    ErrorState(@Nullable ErrorState base, int deriveIndex) {
      myBase = base;
      myDeriveIndex = deriveIndex;
    }

    public ErrorState() {
      this(null, 0);
    }

    @NotNull
    ErrorState deriveFrom(int index) {
      return new ErrorState(this, index);
    }

    void addError(int index, @NotNull Error error) {
      if (myErrors == null) myErrors = new SmartList<Pair<Integer, Error>>();
      Pair<Integer, Error> pair = Pair.create(index, error);
      myErrors.add(pair);

      if (myPattern != null) {
        applyError(myPattern, pair);
      }
    }

    int countErrors(int start, int end) {
      int errors = 0;
      if (myBase != null && start < myDeriveIndex) {
        errors += myBase.countErrors(start, myDeriveIndex);
      }

      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (start <= error.first && error.first <= end) {
            errors++;
          }
        }
      }

      return errors;
    }

    public char[] getPattern(char[] pattern) {
      if (myPattern == null) {
        myPattern = applyErrors(Arrays.copyOf(pattern, pattern.length), pattern.length);
      }
      return myPattern;
    }

    private char[] applyErrors(char[] pattern, int upToIndex) {
      if (myBase != null) {
        myBase.applyErrors(pattern, Math.min(myDeriveIndex, upToIndex));
      }

      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (error.first < upToIndex) {
            applyError(pattern, error);
          }
        }
      }

      return pattern;
    }

    private void applyError(char[] pattern, Pair<Integer, Error> error) {
      if (error.second instanceof TypoError) {
        pattern[error.first] = ((TypoError)error.second).myCorrectChar;
      }
      else if (error.second instanceof SwapError) {
        char c = pattern[error.first];
        pattern[error.first] = pattern[error.first + 1];
        pattern[error.first + 1] = c;
      }
    }

    public boolean affects(int index) {
      //todo optimize
      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (error.first == index) return true;
          if (error.first == index - 1 && error.second == SwapError.instance) return true;
          if (error.first < index) {
            //todo support miss/extra
          }
        }
      }

      if (myBase != null && myDeriveIndex > index) {
        return myBase.affects(index);
      }

      return false;
    }

    public Error getError(int i) {
      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (error.first == i) return error.second;
        }
      }

      if (myBase != null && myDeriveIndex > i) {
        return myBase.getError(i);
      }

      return null;
    }
  }

  private static abstract interface Error {
    enum ErrorKind {
      typo, //there must be a typo
      swap,     //actual symbols are swapped
      miss,     //actual char is missing
      extra
    }
  }

  private static class TypoError implements Error {
    private final char myCorrectChar;

    public TypoError(char correctChar) {
      myCorrectChar = correctChar;
    }
  }

  private static class SwapError implements Error {
    public static final SwapError instance = new SwapError();
  }

  private static class Fragment {
    private final int myLength;
    private final ErrorState myErrorState;

    public Fragment(int length, @NotNull ErrorState errorState) {
      myLength = length;
      myErrorState = errorState;
    }

    int getLength() {return myLength;}

    @NotNull
    ErrorState getErrorState() {return myErrorState;}
  }
}
