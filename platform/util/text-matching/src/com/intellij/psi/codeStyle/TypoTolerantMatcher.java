// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FList;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

final class TypoTolerantMatcher extends MinusculeMatcher {

  //heuristics: 15 can take 10-20 ms in some cases, while 10 works in 1-5 ms
  private static final int TYPO_AWARE_PATTERN_LIMIT = 13;

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
  TypoTolerantMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, @NotNull String hardSeparators) {
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

  private static @NotNull FList<TextRange> prependRange(@NotNull FList<TextRange> ranges, @NotNull Range range) {
    Range head = ((Range)ranges.getHead());
    if (head != null && head.getStartOffset() == range.getEndOffset()) {
      return ranges.getTail().prepend(new Range(range.getStartOffset(), head.getEndOffset(), range.getErrorCount() + head.getErrorCount()));
    }
    return ranges.prepend(range);
  }

  @Override
  public int matchingDegree(@NotNull String name) {
    return matchingDegree(name, false);
  }

  @Override
  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch) {
    return matchingDegree(name, valueStartCaseMatch, matchingFragments(name));
  }

  @Override
  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<? extends TextRange> fragments) {
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

      errors += 2000.0 * Math.pow(1.0 * ((Range)range).getErrorCount() / range.getLength(), 2);
    }

    int startIndex = first.getStartOffset();
    boolean afterSeparator = Strings.indexOfAny(name, myHardSeparators, 0, startIndex) >= 0;
    boolean wordStart = startIndex == 0 || NameUtilCore.isWordStart(name, startIndex) && !NameUtilCore.isWordStart(name, startIndex - 1);
    boolean finalMatch = fragments.get(fragments.size() - 1).getEndOffset() == name.length();

    return (wordStart ? 1000 : 0) +
           matchingCase -
           fragments.size() +
           -skippedHumps * 10 -
           errors +
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
  public boolean isStartMatch(@NotNull String name) {
    FList<TextRange> fragments = matchingFragments(name);
    return fragments != null && isStartMatch(fragments);
  }

  public static boolean isStartMatch(@NotNull Iterable<? extends TextRange> fragments) {
    Iterator<? extends TextRange> iterator = fragments.iterator();
    return !iterator.hasNext() || iterator.next().getStartOffset() == 0;
  }

  @Override
  public boolean matches(@NotNull String name) {
    return matchingFragments(name) != null;
  }

  @Override
  public @NotNull String getPattern() {
    return new String(myPattern);
  }

  @Override
  public @Nullable FList<TextRange> matchingFragments(@NotNull String name) {
    FList<TextRange> ranges = new Session(name, false).matchingFragments();
    if (ranges != null) return ranges;

    //do not apply typo aware matching for long patterns, it can take too much time
    if (myPattern.length > TYPO_AWARE_PATTERN_LIMIT) return null;

    return new Session(name, true).matchingFragments();
  }

  private class Session {
    private final @NotNull String myName;
    private final boolean isAsciiName;

    private final boolean myTypoAware;
    private final boolean myAllowTypos;

    Session(@NotNull String name, boolean typoAware) {
      myName = name;

      int length = name.length();
      boolean isAscii = true;
      for (int i = 0; i < length; ++i) {
        char c = name.charAt(i);
        if (c >= 128) {
          isAscii = false;
          break;
        }
      }

      isAsciiName = isAscii;
      myTypoAware = typoAware;
      myAllowTypos = typoAware && isAscii;
    }

    private char charAt(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? errorState.getChar(myPattern, i) : myPattern[i];
    }

    private char toLowerCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? toLowerAscii(errorState.getChar(myPattern, i)) : toLowerCase[i];
    }

    private char toUpperCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? toUpperAscii(errorState.getChar(myPattern, i)) : toUpperCase[i];
    }

    private boolean isLowerCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? isLowerAscii(errorState.getChar(myPattern, i)) : isLowerCase[i];
    }

    private boolean isUpperCase(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? isUpperAscii(errorState.getChar(myPattern, i)) : isUpperCase[i];
    }

    private boolean isWordSeparator(int i, @NotNull ErrorState errorState) {
      return errorState.affects(i) ? TypoTolerantMatcher.isWordSeparator(errorState.getChar(myPattern, i)) : isWordSeparator[i];
    }

    private int patternLength(@NotNull ErrorState errorState) {
      return errorState.length(myPattern);
    }

    public @Nullable FList<TextRange> matchingFragments() {
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
    private @Nullable FList<TextRange> matchWildcards(int patternIndex,
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
            return FList.<TextRange>emptyList().prepend(new Range(spaceIndex, spaceIndex + 1, 0));
          }
          return null;
        }
        return FList.emptyList();
      }

      FList<TextRange> ranges = matchFragment(patternIndex, nameIndex, errorState);
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
    private @Nullable FList<TextRange> matchSkippingWords(int patternIndex,
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
          FList<TextRange> ranges = matchInsideFragment(patternIndex, nameIndex, fragment);
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
      if (!allowSpecialChars && !myHasSeparators && !myHasHumps && Strings.containsAnyChar(myName, myHardSeparators, startAt, next)) {
        return -1;
      }
      // if the user has typed a dot, don't skip other dots between humps
      // but one pattern dot may match several name dots
      if (!allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.', errorState) && Strings.contains(myName, startAt, next, '.')) {
        return -1;
      }

      return next;
    }

    private boolean seemsLikeFragmentStart(int patternIndex, int nextOccurrence, @NotNull ErrorState errorState) {
      // uppercase should match either uppercase or a word start
      return !isUpperCase(patternIndex, errorState) ||
             Character.isUpperCase(myName.charAt(nextOccurrence)) ||
             NameUtilCore.isWordStart(myName, nextOccurrence) ||
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

      if (errorState.countErrors(0, patternIndex) > 0) return false;
      Error prevError = errorState.getError(patternIndex - 1);
      if (prevError == SwapError.instance) {
        return false;
      }

      char leftMiss = leftMiss(patternChar);
      if (leftMiss != 0) {
        if (leftMiss == nameChar ||
            isIgnoreCase && (toLowerAscii(leftMiss) == nameChar || toUpperAscii(leftMiss) == nameChar)) {
          errorState.addError(patternIndex, new TypoError(leftMiss));
          return true;
        }
      }

      char rightMiss = rightMiss(patternChar);
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

      if (myName.length() > nameIndex + 1) {
        char nextNameChar = myName.charAt(nameIndex + 1);

        if (patternChar == nextNameChar || isIgnoreCase && (toLowerCase(patternIndex, errorState) == nextNameChar || toUpperCase(patternIndex, errorState) == nextNameChar)) {
          errorState.addError(patternIndex, new MissError(nameChar));
          return true;
        }
      }

      return false;
    }

    private @Nullable FList<TextRange> matchFragment(int patternIndex,
                                                     int nameIndex,
                                                     @NotNull ErrorState errorState) {
      Fragment fragment = maxMatchingFragment(patternIndex, nameIndex, errorState);
      return fragment == null ? null : matchInsideFragment(patternIndex, nameIndex, fragment);
    }

    private @Nullable Fragment maxMatchingFragment(int patternIndex, int nameIndex, @NotNull ErrorState baseErrorState) {
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
        i++;
      }
      return new Fragment(i, errorState);
    }

    // we've found the longest fragment matching pattern and name
    private @Nullable FList<TextRange> matchInsideFragment(int patternIndex,
                                             int nameIndex,
                                             @NotNull Fragment fragment) {
      // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
      int minFragment = isMiddleMatch(patternIndex, nameIndex, fragment.getErrorState())
                        ? 3 : 1;

      FList<TextRange> camelHumpRanges = improveCamelHumps(patternIndex, nameIndex, fragment.getLength(), minFragment, fragment.getErrorState());
      if (camelHumpRanges != null) {
        return camelHumpRanges;
      }

      return findLongestMatchingPrefix(patternIndex, nameIndex, fragment.getLength(), minFragment, fragment.getErrorState());
    }

    private boolean isMiddleMatch(int patternIndex, int nameIndex, @NotNull ErrorState errorState) {
      return isPatternChar(patternIndex - 1, '*', errorState) && !isWildcard(patternIndex + 1) &&
             Character.isLetterOrDigit(myName.charAt(nameIndex)) && !NameUtilCore.isWordStart(myName, nameIndex);
    }

    private @Nullable FList<TextRange> findLongestMatchingPrefix(int patternIndex,
                                                                 int nameIndex,
                                                                 int fragmentLength, int minFragment,
                                                                 @NotNull ErrorState errorState) {
      if (patternIndex + fragmentLength >= patternLength(errorState)) {
        int errors = errorState.countErrors(patternIndex, patternIndex + fragmentLength);
        if (errors == fragmentLength) return null;
        return FList.<TextRange>emptyList().prepend(new Range(nameIndex, nameIndex + fragmentLength, errors));
      }

      // try to match the remainder of pattern with the remainder of name
      // it may not succeed with the longest matching fragment, then try shorter matches
      for (int i = fragmentLength; i >= minFragment || isWildcard(patternIndex + i); i--) {
        ErrorState derivedErrorState = errorState.deriveFrom(patternIndex + i);
        FList<TextRange> ranges = isWildcard(patternIndex + i) ?
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
    private @Nullable FList<TextRange> improveCamelHumps(int patternIndex,
                                           int nameIndex,
                                           int maxFragment,
                                           int minFragment,
                                           @NotNull ErrorState errorState) {
      for (int i = minFragment; i < maxFragment; i++) {
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i, errorState)) {
          FList<TextRange> ranges = findUppercaseMatchFurther(patternIndex + i, nameIndex + i, errorState.deriveFrom(patternIndex + i));
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

    private @Nullable FList<TextRange> findUppercaseMatchFurther(int patternIndex,
                                                                 int nameIndex,
                                                                 @NotNull ErrorState errorState) {
      int nextWordStart = indexOfWordStart(patternIndex, nameIndex, errorState);
      return matchWildcards(patternIndex, nextWordStart, errorState.deriveFrom(patternIndex));
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
      if (isAsciiName && Strings.isAscii(p)) {
        int i = indexIgnoringCaseAscii(fromIndex, patternIndex, p);
        if (i != -1) return i;

        if (myAllowTypos) {
          int leftMiss = indexIgnoringCaseAscii(fromIndex, patternIndex, leftMiss(p));
          if (leftMiss != -1) return leftMiss;

          int rightMiss = indexIgnoringCaseAscii(fromIndex, patternIndex, rightMiss(p));
          if (rightMiss != -1) return rightMiss;
        }

        return -1;
      }
      return Strings.indexOfIgnoreCase(myName, p, fromIndex);
    }

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

  @Override
  public @NonNls String toString() {
    return "TypoTolerantMatcher{myPattern=" + new String(myPattern) + ", myOptions=" + myOptions + '}';
  }


  private static class ErrorState {
    private final @Nullable ErrorState myBase;
    private final int myDeriveIndex;

    private List<Pair<Integer, Error>> myErrors;

    private char[] myPattern;

    ErrorState(@Nullable ErrorState base, int deriveIndex) {
      myBase = base;
      myDeriveIndex = deriveIndex;
    }

    ErrorState() {
      this(null, 0);
    }

    @NotNull
    ErrorState deriveFrom(int index) {
      return new ErrorState(this, index);
    }

    void addError(int index, @NotNull Error error) {
      if (myErrors == null) myErrors = new SmartList<>();
      Pair<Integer, Error> pair = Pair.create(index, error);
      myErrors.add(pair);

      if (myPattern != null) {
        myPattern = applyError(myPattern, pair);
      }
    }

    int countErrors(int start, int end) {
      int errors = 0;
      if (myBase != null && start < myDeriveIndex) {
        errors += myBase.countErrors(start, myDeriveIndex);
      }

      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (start <= error.first && error.first < end) {
            errors++;
          }
        }
      }

      return errors;
    }

    private boolean processErrors(int start, int end, Processor<? super Pair<Integer, Error>> processor) {
      if (myBase != null && start < myDeriveIndex) {
        if (!myBase.processErrors(start, myDeriveIndex, processor)) {
          return false;
        }
      }

      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (start <= error.first && error.first < end) {
            if (!processor.process(error)) {
              return false;
            }
          }
        }
      }

      return true;
    }

    public char getChar(char[] pattern, int index) {
      if (myPattern == null) {
        myPattern = applyErrors(pattern.clone(), Integer.MAX_VALUE);
      }

      return myPattern[index];
    }

    private char[] applyErrors(char[] pattern, int upToIndex) {
      if (myBase != null) {
        pattern = myBase.applyErrors(pattern, Math.min(myDeriveIndex, upToIndex));
      }

      if (myErrors != null) {
        for (Pair<Integer, Error> error : myErrors) {
          if (error.first < upToIndex) {
            pattern = applyError(pattern, error);
          }
        }
      }

      return pattern;
    }

    private static char[] applyError(char[] pattern, Pair<Integer, Error> error) {
      if (error.second instanceof TypoError) {
        pattern[error.first] = ((TypoError)error.second).myCorrectChar;
        return pattern;
      }
      else if (error.second instanceof SwapError) {
        char c = pattern[error.first];
        pattern[error.first] = pattern[error.first + 1];
        pattern[error.first + 1] = c;
        return pattern;
      }
      else if (error.second instanceof MissError) {
        return ArrayUtil.insert(pattern, error.first, ((MissError)error.second).myMissedChar);
      }

      return pattern;
    }

    public boolean affects(final int index) {
      return !processErrors(0, index + 1, error -> {
        if (error.first == index) return false;
        if (error.first == index - 1 && error.second == SwapError.instance) return false;
        if (error.first < index) {
          if (error.second instanceof MissError) return false;
          //todo support extra
        }
        return true;
      });
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

    public int length(char[] pattern) {
      final Ref<Integer> ref = new Ref<>(pattern.length);
      processErrors(0, Integer.MAX_VALUE, error -> {
        if (error.second instanceof MissError) {
          ref.set(ref.get() + 1);
        }
        return true;
      });
      return ref.get();
    }
  }

  private interface Error { }

  private static class TypoError implements Error {
    private final char myCorrectChar;

    TypoError(char correctChar) {
      myCorrectChar = correctChar;
    }
  }

  private static class SwapError implements Error {
    public static final SwapError instance = new SwapError();
  }

  private static class MissError implements Error {
    private final char myMissedChar;

    MissError(char missedChar) {
      myMissedChar = missedChar;
    }
  }

  private static class Fragment {
    private final int myLength;
    private final ErrorState myErrorState;

    Fragment(int length, @NotNull ErrorState errorState) {
      myLength = length;
      myErrorState = errorState;
    }

    int getLength() {return myLength;}

    @NotNull
    ErrorState getErrorState() {return myErrorState;}
  }

  private static class Range extends TextRange {
    private final int myErrorCount;

    Range(int startOffset, int endOffset, int errorCount) {
      super(startOffset, endOffset);
      myErrorCount = errorCount;
    }

    public int getErrorCount() {
      return myErrorCount;
    }

    @Override
    public @NotNull Range shiftRight(int delta) {
      if (delta == 0) return this;
      return new Range(getStartOffset() + delta, getEndOffset() + delta, getErrorCount());
    }

    public static Range from(int from, int length) {
      return new Range(from, from + length, 0);
    }
  }


  private static final char[][] keyboard = {
    {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'},
    {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'},
    {'z', 'x', 'c', 'v', 'b', 'n', 'm'}
  };

  private static char leftMiss(char aChar) {
    boolean isUpperCase = isUpperAscii(aChar);
    char lc = isUpperCase ? toLowerAscii(aChar) : aChar;

    for (char[] line : keyboard) {
      for (int j = 0; j < line.length; j++) {
        char c = line[j];
        if (c == lc) {
          if (j > 0) {
            return isUpperCase ? toUpperAscii(line[j - 1]) : line[j - 1];
          }
          else {
            return 0;
          }
        }
      }
    }
    return 0;
  }

  private static char rightMiss(char aChar) {
    boolean isUpperCase = isUpperAscii(aChar);
    char lc = isUpperCase ? toLowerAscii(aChar) : aChar;

    for (char[] line : keyboard) {
      for (int j = 0; j < line.length; j++) {
        char c = line[j];
        if (c == lc) {
          if (j + 1 < line.length) {
            return isUpperCase ? toUpperAscii(line[j + 1]) : line[j + 1];
          }
          else {
            return 0;
          }
        }
      }
    }
    return 0;
  }
}
