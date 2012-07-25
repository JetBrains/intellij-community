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
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

import static com.intellij.psi.codeStyle.NameUtil.MatchingCaseSensitivity.*;

/**
* @author peter
*/
public class MinusculeMatcher implements Matcher {
  private final char[] myPattern;
  private final NameUtil.MatchingCaseSensitivity myOptions;

  public MinusculeMatcher(String pattern, NameUtil.MatchingCaseSensitivity options) {
    myOptions = options;
    myPattern = StringUtil.trimEnd(pattern, "* ").toCharArray();
  }

  @Nullable
  private FList<TextRange> matchName(String name, int patternIndex, int nameIndex) {
    if (patternIndex == myPattern.length) {
      return FList.emptyList();
    }
    if ('*' == myPattern[patternIndex]) {
      return skipChars(name, patternIndex, nameIndex, true);
    }
    if (nameIndex == name.length()) {
      return null;
    }

    if ('.' == myPattern[patternIndex] && name.charAt(nameIndex) != '.') {
      return skipChars(name, patternIndex, nameIndex, false);
    }

    if (' ' == myPattern[patternIndex] && patternIndex != myPattern.length - 1) {
      return skipWords(name, patternIndex, nameIndex);
    }

    if ((patternIndex == 0 || patternIndex == 1 && myPattern[0] == ' ' && nameIndex == 0) &&
        myOptions != NameUtil.MatchingCaseSensitivity.NONE && name.charAt(nameIndex) != myPattern[patternIndex]) {
      return null;
    }

    if (NameUtil.isWordSeparator(name.charAt(nameIndex))) {
      return skipSeparators(name, patternIndex, nameIndex);
    }

    if (StringUtil.toLowerCase(name.charAt(nameIndex)) != StringUtil.toLowerCase(myPattern[patternIndex])) {
      if (Character.isDigit(name.charAt(nameIndex)) && nameIndex > 0 || (name.charAt(nameIndex) == '.' && name.indexOf('.', nameIndex + 1) > 0)) {
        return matchName(name, patternIndex, nameIndex + 1);
      }
      return null;
    }

    if (myOptions == ALL && name.charAt(nameIndex) != myPattern[patternIndex]) {
      return null;
    }

    int nextStart = NameUtil.nextWord(name, nameIndex);

    int lastUpper = NameUtil.isWordStart(myPattern[patternIndex]) ? 0 : -1;

    int i = 1;
    while (true) {
      if (patternIndex + i == myPattern.length || i + nameIndex == nextStart) {
        break;
      }
      char p = myPattern[patternIndex + i];
      char w = name.charAt(i + nameIndex);
      if (lastUpper == i - 1 && NameUtil.isWordStart(p) && myOptions != ALL) {
        if (p == w) {
          lastUpper = i;
        }
        p = StringUtil.toLowerCase(p);
      }

      if (myOptions != ALL) {
        w = StringUtil.toLowerCase(w);
      }
      if (w != p) {
        break;
      }
      i++;
    }

    if (isFinalSpaceMatch(name, patternIndex, nameIndex, nextStart, i)) {
      return FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i));
    }

    return matchAfterFragment(name, patternIndex, nameIndex, nextStart, lastUpper, i);
  }

  private boolean isFinalSpaceMatch(String name, int patternIndex, int nameIndex, int nextStart, int i) {
    return nextStart == name.length() &&
        patternIndex + i == myPattern.length - 1 &&
        ' ' == myPattern[patternIndex + i] &&
        (i == 1 && NameUtil.isWordStart(myPattern[patternIndex]) || i + nameIndex == name.length());
  }

  @Nullable
  private FList<TextRange> matchAfterFragment(String name, int patternIndex, int nameIndex, int nextStart, int lastUpper, int matchLen) {
    boolean star = patternIndex + matchLen < myPattern.length && myPattern[patternIndex + matchLen] == '*';
    if (lastUpper >= 1) {
      int nextNameIndex = star && matchLen == lastUpper ? nameIndex + lastUpper : nextStart;
      int nextPatternIndex = patternIndex + lastUpper + 1;
      FList<TextRange> ranges = star ? matchName(name, nextPatternIndex, nextNameIndex) : skipWords(name, nextPatternIndex, nextNameIndex);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, lastUpper + 1);
      }
    }

    int trial = matchLen;
    while (trial > 0) {
      FList<TextRange> ranges = skipWords(name, patternIndex + trial, nextStart);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, trial);
      }
      trial--;
    }

    FList<TextRange> ranges = matchName(name, patternIndex + matchLen, nameIndex + matchLen);
    if (ranges != null) {
      return prependRange(ranges, nameIndex, matchLen);
    }
    return null;
  }

  private static FList<TextRange> prependRange(FList<TextRange> ranges, int from, int length) {
    TextRange head = ranges.getHead();
    if (head != null && head.getStartOffset() == from + length) {
      return ranges.getTail().prepend(new TextRange(from, head.getEndOffset()));
    }
    return ranges.prepend(TextRange.from(from, length));
  }

  @Nullable
  private FList<TextRange> skipSeparators(String name, int patternIndex, int nameIndex) {
    int nextStart = NameUtil.nextWord(name, nameIndex);
    assert nextStart - nameIndex == 1 : "'" + name + "'" + nameIndex + " " + nextStart;
    char p = myPattern[patternIndex];
    if (NameUtil.isWordSeparator(p)) {
      if (myOptions != NameUtil.MatchingCaseSensitivity.NONE &&
          nameIndex == 0 && name.length() > 1 && patternIndex + 1 < myPattern.length &&
          NameUtil.isWordSeparator(name.charAt(1)) && !NameUtil.isWordSeparator(myPattern[patternIndex + 1])) {
        return null;
      }

      final FList<TextRange> ranges = matchName(name, patternIndex + 1, nextStart);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, 1);
      }

      return null;
    }

    return matchName(name, patternIndex, nextStart);
  }

  @Nullable
  private FList<TextRange> skipChars(String name, int patternIndex, int nameIndex, boolean maySkipNextChar) {
    boolean veryStart = patternIndex == 0;
    while ('*' == myPattern[patternIndex]) {
      patternIndex++;
      if (patternIndex == myPattern.length) {
        return FList.emptyList();
      }
    }

    char nextChar = myPattern[patternIndex];
    boolean upper = Character.isUpperCase(myPattern[patternIndex]);

    int fromIndex = nameIndex;
    while (true) {
      int next = StringUtil.indexOfIgnoreCase(name, nextChar, fromIndex);
      if (next < 0) {
        break;
      }
      if (next == 0 && myOptions != NameUtil.MatchingCaseSensitivity.NONE && name.charAt(next) != nextChar) {
        fromIndex = next + 1;
        continue;
      }
      if (upper && next > 0 && !Character.isUpperCase(name.charAt(next))) {
        fromIndex = next + 1;
        continue;
      }

      if (veryStart && next > 0 && !NameUtil.isWordStart(name, next) && !NameUtil.isWordSeparator(nextChar)) {
        if (next == name.length() - 1) {
          return null;
        }
        if (patternIndex == myPattern.length - 1 ||
            Character.isLetter(myPattern[patternIndex + 1]) && (myPattern[patternIndex + 1] != name.charAt(next + 1) ||
                                                                NameUtil.isWordStart(name, next + 1))) {
          fromIndex = next + 1;
          continue;
        }
      }

      FList<TextRange> ranges = matchName(name, patternIndex, next);
      if (ranges != null) {
        return ranges;
      }
      if (!maySkipNextChar) {
        return null;
      }
      fromIndex = next + 1;
    }
    return null;
  }

  @Nullable
  private FList<TextRange> skipWords(String name, int patternIndex, int nameIndex) {
    while (patternIndex < myPattern.length && ' ' == myPattern[patternIndex]) {
      patternIndex++;
    }
    if (patternIndex == myPattern.length) {
      return FList.emptyList();
    }
    if (nameIndex == name.length()) {
      return null;
    }
    if (myPattern[patternIndex] == '*') {
      return skipChars(name, patternIndex, nameIndex, true);
    }

    if (nameIndex == 0 || NameUtil.isWordStart(name, nameIndex)) {
      FList<TextRange> ranges = matchName(name, patternIndex, nameIndex);
      if (ranges != null) {
        return ranges;
      }
    }

    boolean separatorInPattern = !Character.isLetterOrDigit(myPattern[patternIndex]);
    int fromIndex = nameIndex;
    while (fromIndex < name.length()) {
      int next = separatorInPattern ? name.indexOf(myPattern[patternIndex], fromIndex) : NameUtil.nextWord(name, fromIndex);
      if (next < 0) {
        break;
      }

      FList<TextRange> ranges = matchName(name, patternIndex, next);
      if (ranges != null) {
        return ranges;
      }
      fromIndex = next;
      if (separatorInPattern) {
        fromIndex++;
      }
    }
    return null;
  }

  public int matchingDegree(String name) {
    Iterable<TextRange> iterable = matchingFragments(name);
    if (iterable == null) return Integer.MIN_VALUE;

    int fragmentCount = 0;
    int matchingCase = 0;
    CharArrayCharSequence seq = new CharArrayCharSequence(myPattern);
    int p = -1;
    TextRange first = null;
    for (TextRange range : iterable) {
      if (first == null) {
        first = range;
      }
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
    boolean middleWordStart = !prefixMatching && NameUtil.isWordStart(name, first.getStartOffset());

    return -fragmentCount + matchingCase * 2 + commonStart * 3 - startIndex + (prefixMatching ? 2 : middleWordStart ? 1 : 0) * 100;
  }

  public boolean isStartMatch(String name) {
    Iterable<TextRange> fragments = matchingFragments(name);
    if (fragments != null) {
      Iterator<TextRange> iterator = fragments.iterator();
      if (!iterator.hasNext() || isStartMatch(name, iterator.next().getStartOffset())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isStartMatch(String name, int startIndex) {
    for (int i = 0; i < startIndex; i++) {
      if (!NameUtil.isWordSeparator(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean matches(String name) {
    return matchingFragments(name) != null;
  }

  @Nullable
  public Iterable<TextRange> matchingFragments(String name) {
    if (name.isEmpty()) {
      return myPattern.length == 0 ? Collections.<TextRange>emptyList() : null;
    }

    if (true) {
      return matchWildcards(name, 0, 0);
    }

    return matchName(name, 0, 0);
  }

  @Nullable
  private FList<TextRange> matchWildcards(String name, int patternIndex, int nameIndex) {
    while (isWildcard(patternIndex)) {
      patternIndex++;
    }
    boolean space = isPatternChar(patternIndex - 1, ' ');
    if (patternIndex == myPattern.length) {
      if (space && nameIndex != name.length() && (patternIndex < 2 || !NameUtil.isWordStart(myPattern[patternIndex - 2]))) {
        return null;
      }
      return FList.emptyList();
    }

    char p = myPattern[patternIndex];
    if (isWildcard(patternIndex - 1)) {
      {
        FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex);
        if (ranges != null) {
          return ranges;
        }
      }
      while (true) {
        nameIndex = space ? indexOfWordStart(name, p, nameIndex) : StringUtil.indexOfIgnoreCase(name, p, nameIndex + 1);
        if (nameIndex < 0) return null;
        if (!Character.isUpperCase(p) || NameUtil.isWordStart(name, nameIndex)) {
          FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex);
          if (ranges != null) {
            return ranges;
          }
        }
      }
    }
    return matchFragment(name, patternIndex, nameIndex);
  }

  @Nullable
  private FList<TextRange> matchFragment(String name, int patternIndex, int nameIndex) {
    if (!isFirstCharMatching(name, nameIndex, myPattern[patternIndex])) {
      return null;
    }

    int minFragment = isPatternChar(patternIndex - 1, '*') && Character.isLetterOrDigit(name.charAt(nameIndex)) && !NameUtil.isWordStart(name, nameIndex) ? 2 : 1;
    int i = 1;
    while (nameIndex + i < name.length() &&
           patternIndex + i < myPattern.length &&
           StringUtil.charsEqual(myPattern[patternIndex + i], name.charAt(nameIndex + i), myOptions != ALL)) {
      if (Character.isUpperCase(myPattern[patternIndex + i])) {
        if (i < minFragment) {
          return null;
        }
        if (myPattern[patternIndex + i] != name.charAt(nameIndex + i)) {
          int nextWordStart = indexOfWordStart(name, myPattern[patternIndex + i], nameIndex + i);
          if (nextWordStart >= 0) {
            FList<TextRange> ranges = matchWildcards(name, patternIndex + i, nextWordStart);
            if (ranges != null) {
              return prependRange(ranges, nameIndex, i);
            }
          }
        }
      }
      i++;
    }

    if (patternIndex + i >= myPattern.length) {
      return i >= minFragment ? FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i)) : null;
    }
    while (i >= minFragment) {
      int nextWordStart = isWildcard(patternIndex + i) ? nameIndex + i : indexOfWordStart(name, myPattern[patternIndex + i], nameIndex + i);
      if (nextWordStart >= 0) {
        FList<TextRange> ranges = matchWildcards(name, patternIndex + i, nextWordStart);
        if (ranges != null) {
          return prependRange(ranges, nameIndex, i);
        }
      }
      i--;
    }
    return null;
  }

  private boolean isFirstCharMatching(String name, int nameIndex, char p) {
    return nameIndex < name.length() && StringUtil.charsEqual(p, name.charAt(nameIndex),
                                                              myOptions == FIRST_LETTER && nameIndex > 0 || myOptions == NONE);
  }

  private boolean isWildcard(int patternIndex) {
    return isPatternChar(patternIndex, ' ') || isPatternChar(patternIndex, '*');
  }
  private boolean isPatternChar(int patternIndex, char c) {
    if (patternIndex < 0 || patternIndex >= myPattern.length) return false;
    return myPattern[patternIndex] == c;
  }

  private static int indexOfWordStart(String name, char p, int startFrom) {
    if (p == '.' || NameUtil.isWordSeparator(p)) {
      return StringUtil.indexOfIgnoreCase(name, p, startFrom + 1);
    }
    if (startFrom >= name.length()) {
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

  @Override
  public String toString() {
    return "MinusculeMatcher{" +
           "myPattern=" + new String(myPattern) +
           ", myOptions=" + myOptions +
           '}';
  }
}
