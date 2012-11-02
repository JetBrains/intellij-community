/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
* @author peter
*/
public class MinusculeMatcher implements Matcher {
  private final char[] myPattern;
  private final NameUtil.MatchingCaseSensitivity myOptions;
  private final boolean myHasHumps;
  private final boolean myHasDots;

  public MinusculeMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options) {
    myOptions = options;
    myPattern = StringUtil.trimEnd(pattern, "* ").toCharArray();

    int i = 0;
    while (isWildcard(i)) i++;
    myHasHumps = hasHumps(i + 1);
    myHasDots = hasDots(i);
  }

  private boolean hasHumps(int start) {
    int i = start;
    while (i < myPattern.length) {
      if (Character.isUpperCase(myPattern[i])) {
        return true;
      }
      i++;
    }
    return false;
  }

  private boolean hasDots(int start) {
    int i = start;
    while (i < myPattern.length) {
      if (myPattern[i] == '.') {
        return true;
      }
      i++;
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
    Iterable<TextRange> iterable = matchingFragments(name);
    if (iterable == null) return Integer.MIN_VALUE;

    int fragmentCount = 0;
    int matchingCase = 0;
    CharArrayCharSequence seq = new CharArrayCharSequence(myPattern);
    int p = -1;
    TextRange first = null;

    int integral = 0;
    for (TextRange range : iterable) {
      if (first == null) {
        first = range;
      }
      int len = range.getLength();
      integral += range.getStartOffset() * len + len * (len - 1) / 2;
      for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
        char c = name.charAt(i);
        p = StringUtil.indexOf(seq, c, p + 1, myPattern.length, false);
        if (p < 0) {
          break;
        }
        if (Character.isUpperCase(myPattern[p]) || i == range.getStartOffset()) {
          matchingCase += c == myPattern[p] ? 1 : 0;
        }
      }
      fragmentCount++;
    }

    if (first == null) {
      return 0;
    }


    int skipCount = CharArrayUtil.shiftForward(myPattern, 0, " *");
    int commonStart = 0;
    while (commonStart < name.length() &&
           commonStart + skipCount < myPattern.length &&
           name.charAt(commonStart) == myPattern[commonStart + skipCount]) {
      commonStart++;
    }

    int startIndex = first.getStartOffset();
    boolean prefixMatching = isStartMatch(name, startIndex);
    boolean middleWordStart = !prefixMatching && startIndex > 0 && NameUtil.isWordStart(name, startIndex) && !NameUtil.isWordStart(name, startIndex - 1);

    return -fragmentCount + matchingCase * 20 + commonStart * 30 - startIndex + (prefixMatching ? 2 : middleWordStart ? 1 : 0) * 1000 - integral;
  }

  public boolean isStartMatch(@NotNull String name) {
    Iterable<TextRange> fragments = matchingFragments(name);
    if (fragments != null) {
      Iterator<TextRange> iterator = fragments.iterator();
      if (!iterator.hasNext() || isStartMatch(name, iterator.next().getStartOffset())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isStartMatch(@NotNull String name, int startIndex) {
    for (int i = 0; i < startIndex; i++) {
      if (!NameUtil.isWordSeparator(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean matches(@NotNull String name) {
    return matchingFragments(name) != null;
  }

  @Nullable
  public Iterable<TextRange> matchingFragments(@NotNull String name) {
    return matchWildcards(name, 0, 0);
  }

  @Nullable
  private FList<TextRange> matchWildcards(@NotNull String name, int patternIndex, int nameIndex) {
    if (nameIndex < 0) {
      return null;
    }
    if (!isWildcard(patternIndex)) {
      if (patternIndex == myPattern.length) {
        return FList.emptyList();
      }
      return matchFragment(name, patternIndex, nameIndex);
    }

    do {
      patternIndex++;
    } while (isWildcard(patternIndex));
    boolean space = isPatternChar(patternIndex - 1, ' ');
    if (patternIndex == myPattern.length) {
      if (space && nameIndex != name.length() && (patternIndex < 2 || !NameUtil.isWordStart(myPattern[patternIndex - 2]))) {
        return null;
      }
      return FList.emptyList();
    }

    FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex);
    if (ranges != null) {
      return ranges;
    }

    return matchSkippingWords(name, patternIndex, nameIndex, true);
  }

  @Nullable
  private FList<TextRange> matchSkippingWords(@NotNull String name, int patternIndex, int nameIndex, boolean allowSpecialChars) {
    boolean star = isPatternChar(patternIndex - 1, '*');
    char p = myPattern[patternIndex];
    while (true) {
      int nextOccurrence = star ? StringUtil.indexOfIgnoreCase(name, p, nameIndex + 1) : indexOfWordStart(name, patternIndex, nameIndex);
      if (nextOccurrence < 0) {
        return null;
      }
      if (!allowSpecialChars && !myHasHumps && StringUtil.containsAnyChar(name, " ()", nameIndex, nextOccurrence)) {
        return null;
      }
      if (myHasDots && StringUtil.contains(name, nameIndex, nextOccurrence, '.')) {
        return null;
      }
      if (!Character.isUpperCase(p) || NameUtil.isWordStart(name, nextOccurrence)) {
        FList<TextRange> ranges = matchFragment(name, patternIndex, nextOccurrence);
        if (ranges != null) {
          return ranges;
        }
      }
      nameIndex = nextOccurrence;
    }
  }

  @Nullable
  private FList<TextRange> matchFragment(@NotNull String name, int patternIndex, int nameIndex) {
    if (!isFirstCharMatching(name, nameIndex, myPattern[patternIndex])) {
      return null;
    }

    int minFragment = isPatternChar(patternIndex - 1, '*') && Character.isLetterOrDigit(name.charAt(nameIndex)) && !NameUtil.isWordStart(name, nameIndex) ? 2 : 1;
    int i = 1;
    while (nameIndex + i < name.length() &&
           patternIndex + i < myPattern.length &&
           StringUtil.charsEqual(myPattern[patternIndex + i], name.charAt(nameIndex + i), myOptions != NameUtil.MatchingCaseSensitivity.ALL)) {
      if (Character.isUpperCase(myPattern[patternIndex + i])) {
        if (i < minFragment) {
          return null;
        }
        if (myPattern[patternIndex + i] != name.charAt(nameIndex + i)) {
          int nextWordStart = indexOfWordStart(name, patternIndex + i, nameIndex + i);
          FList<TextRange> ranges = matchWildcards(name, patternIndex + i, nextWordStart);
          if (ranges != null) {
            return prependRange(ranges, nameIndex, i);
          }
        }
      }
      i++;
    }

    if (patternIndex + i >= myPattern.length) {
      return FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i));
    }
    while (i >= minFragment) {
      FList<TextRange> ranges = isWildcard(patternIndex + i) ?
                                matchWildcards(name, patternIndex + i, nameIndex + i) :
                                matchSkippingWords(name, patternIndex + i, nameIndex + i, false);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, i);
      }
      i--;
    }
    return null;
  }

  private boolean isFirstCharMatching(@NotNull String name, int nameIndex, char p) {
    return nameIndex < name.length() && StringUtil.charsEqual(p, name.charAt(nameIndex),
                                                              myOptions == NameUtil.MatchingCaseSensitivity.FIRST_LETTER && nameIndex > 0 ||
                                                              myOptions == NameUtil.MatchingCaseSensitivity.NONE);
  }

  private boolean isWildcard(int patternIndex) {
    return isPatternChar(patternIndex, ' ') || isPatternChar(patternIndex, '*');
  }
  private boolean isPatternChar(int patternIndex, char c) {
    return patternIndex >= 0 && patternIndex < myPattern.length && myPattern[patternIndex] == c;
  }

  private int indexOfWordStart(@NotNull String name, int patternIndex, int startFrom) {
    char p = myPattern[patternIndex];
    if (p == '.' || NameUtil.isWordSeparator(p)) {
      return StringUtil.indexOfIgnoreCase(name, p, startFrom + 1);
    }
    if (startFrom >= name.length() ||
        myHasHumps && Character.isLowerCase(p) && !(patternIndex > 0 && NameUtil.isWordSeparator(myPattern[patternIndex - 1]))) {
      return -1;
    }
    int nextWordStart = startFrom;
    while (true) {
      nextWordStart = NameUtil.nextWord(name, nextWordStart);
      if (nextWordStart >= name.length()) {
        return -1;
      }
      if (StringUtil.charsEqualIgnoreCase(p, name.charAt(nextWordStart))) {
        return nextWordStart;
      }
    }
  }

  @NonNls
  @Override
  public String toString() {
    return "MinusculeMatcher{" +
           "myPattern=" + new String(myPattern) +
           ", myOptions=" + myOptions +
           '}';
  }
}
