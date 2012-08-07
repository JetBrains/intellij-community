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

import java.util.Iterator;

import static com.intellij.psi.codeStyle.NameUtil.MatchingCaseSensitivity.*;

/**
* @author peter
*/
public class MinusculeMatcher implements Matcher {
  private final char[] myPattern;
  private final NameUtil.MatchingCaseSensitivity myOptions;
  private final boolean myHasHumps;

  public MinusculeMatcher(String pattern, NameUtil.MatchingCaseSensitivity options) {
    myOptions = options;
    myPattern = StringUtil.trimEnd(pattern, "* ").toCharArray();
    int i = 0;
    while (isWildcard(i)) i++;
    i++;
    while (i < myPattern.length) {
      if (Character.isUpperCase(myPattern[i])) {
        myHasHumps = true;
        return;
      }
      i++;
    }
    myHasHumps = false;
  }

  private static FList<TextRange> prependRange(FList<TextRange> ranges, int from, int length) {
    TextRange head = ranges.getHead();
    if (head != null && head.getStartOffset() == from + length) {
      return ranges.getTail().prepend(new TextRange(from, head.getEndOffset()));
    }
    return ranges.prepend(TextRange.from(from, length));
  }

  public int matchingDegree(String name) {
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
    return matchWildcards(name, 0, 0);
  }

  @Nullable
  private FList<TextRange> matchWildcards(String name, int patternIndex, int nameIndex) {
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

    {
      FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex);
      if (ranges != null) {
        return ranges;
      }
    }

    char p = myPattern[patternIndex];
    while (true) {
      nameIndex = space ? indexOfWordStart(name, patternIndex, nameIndex) : StringUtil.indexOfIgnoreCase(name, p, nameIndex + 1);
      if (nameIndex < 0) {
        return null;
      }
      if (!Character.isUpperCase(p) || NameUtil.isWordStart(name, nameIndex)) {
        FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex);
        if (ranges != null) {
          return ranges;
        }
      }
    }
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
      return i >= minFragment ? FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i)) : null;
    }
    while (i >= minFragment) {
      int nextWordStart;
      if (isWildcard(patternIndex + i)) {
        nextWordStart = nameIndex + i;
      }
      else {
        nextWordStart = indexOfWordStart(name, patternIndex + i, nameIndex + i);
        if (!myHasHumps && StringUtil.containsAnyChar(name, " ()", nameIndex + i, nextWordStart)) {
          nextWordStart = -1;
        }
      }
      FList<TextRange> ranges = matchWildcards(name, patternIndex + i, nextWordStart);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, i);
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

  private int indexOfWordStart(String name, int patternIndex, int startFrom) {
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

  @Override
  public String toString() {
    return "MinusculeMatcher{" +
           "myPattern=" + new String(myPattern) +
           ", myOptions=" + myOptions +
           '}';
  }
}
