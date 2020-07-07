// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.Strings;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class StringSearcher {
  private final String myPattern;
  private final char[] myPatternArray;
  private final int myPatternLength;
  private final int[] mySearchTable = new int[128];
  private final boolean myCaseSensitive;
  private final boolean myLowercaseTransform;
  private final boolean myForwardDirection;
  private final boolean myJavaIdentifier;
  private final boolean myHandleEscapeSequences;

  public int getPatternLength() {
    return myPatternLength;
  }

  public StringSearcher(@NotNull String pattern, boolean caseSensitive, boolean forwardDirection) {
    this(pattern, caseSensitive, forwardDirection, false);
  }

  public StringSearcher(@NotNull String pattern, boolean caseSensitive, boolean forwardDirection, boolean handleEscapeSequences) {
    this(pattern, caseSensitive, forwardDirection, handleEscapeSequences, true);
  }

  public StringSearcher(@NotNull String pattern,
                        boolean caseSensitive,
                        boolean forwardDirection,
                        boolean handleEscapeSequences,
                        boolean lookForJavaIdentifiersOnlyIfPossible) {
    myHandleEscapeSequences = handleEscapeSequences;
    if (pattern.isEmpty()) throw new IllegalArgumentException("pattern is empty");
    myPattern = pattern;
    myCaseSensitive = caseSensitive;
    myForwardDirection = forwardDirection;
    char[] chars = myCaseSensitive ? myPattern.toCharArray() : Strings.toLowerCase(myPattern).toCharArray();
    if (chars.length != myPattern.length()) {
      myLowercaseTransform = false;
      chars = Strings.toUpperCase(myPattern).toCharArray();
    } else {
      myLowercaseTransform = true;
    }
    myPatternArray = chars;
    myPatternLength = myPatternArray.length;
    Arrays.fill(mySearchTable, -1);
    myJavaIdentifier = lookForJavaIdentifiersOnlyIfPossible &&
                       Character.isJavaIdentifierPart(pattern.charAt(0)) &&
                       Character.isJavaIdentifierPart(pattern.charAt(pattern.length() - 1));
  }

  @NotNull
  public String getPattern(){
    return myPattern;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public boolean isJavaIdentifier() {
    return myJavaIdentifier;
  }

  public boolean isHandleEscapeSequences() {
    return myHandleEscapeSequences;
  }

  public int scan(@NotNull CharSequence text) {
    return scan(text,0,text.length());
  }

  public int scan(@NotNull CharSequence text, int _start, int _end) {
    return scan(text, null, _start, _end);
  }

  public int @NotNull [] findAllOccurrences(@NotNull CharSequence text) {
    int end = text.length();
    TIntArrayList result = new TIntArrayList();
    for (int index = 0; index < end; index++) {
      //noinspection AssignmentToForLoopParameter
      index = scan(text, index, end);
      if (index < 0) break;
      result.add(index);
    }
    return result.toNativeArray();
  }


  public boolean processOccurrences(@NotNull CharSequence text, @NotNull TIntProcedure consumer) {
    int end = text.length();

    for (int index = 0; index < end; index++) {
      //noinspection AssignmentToForLoopParameter
      index = scan(text, index, end);
      if (index < 0) break;
      if (!consumer.execute(index)) return false;
    }
    return true;
  }

  public int scan(@NotNull CharSequence text, char @Nullable [] textArray, int _start, int _end) {
    if (_start > _end) {
      throw new AssertionError("start > end, " + _start + ">" + _end);
    }
    final int textLength = text.length();
    if (_end > textLength) {
      throw new AssertionError("end > length, " + _end + ">" + textLength);
    }
    if (myForwardDirection) {
      if (myPatternLength == 1) {
        // optimization
        return Strings.indexOf(text, myPatternArray[0], _start, _end, myCaseSensitive);
      }
      int start = _start;
      int end = _end - myPatternLength;

      while (start <= end) {
        int i = myPatternLength - 1;
        char lastChar = normalizedCharAt(text, textArray, start + i);

        if (isSameChar(myPatternArray[i], lastChar)) {
          i--;
          while (i >= 0) {
            char c = textArray != null ? textArray[start + i] : text.charAt(start + i);
            if (!isSameChar(myPatternArray[i], c)) break;
            i--;
          }
          if (i < 0) {
            return start;
          }
        }

        int step = lastChar < 128 ? mySearchTable[lastChar] : 1;

        if (step <= 0) {
          int index;
          for (index = myPatternLength - 2; index >= 0; index--) {
            if (myPatternArray[index] == lastChar) break;
          }
          step = myPatternLength - index - 1;
          mySearchTable[lastChar] = step;
        }

        start += step;
      }
    }
    else {
      int start = 1;
      while (start <= _end - myPatternLength + 1) {
        int i = myPatternLength - 1;
        char lastChar = normalizedCharAt(text, textArray, _end - (start + i));

        if (isSameChar(myPatternArray[myPatternLength - 1 - i], lastChar)) {
          i--;
          while (i >= 0) {
            char c = textArray != null ? textArray[_end - (start + i)] : text.charAt(_end - (start + i));
            if (!isSameChar(myPatternArray[myPatternLength - 1 - i], c)) break;
            i--;
          }
          if (i < 0) return _end - start - myPatternLength + 1;
        }

        int step = lastChar < 128 ? mySearchTable[lastChar] : 1;

        if (step <= 0) {
          int index;
          for (index = myPatternLength - 2; index >= 0; index--) {
            if (myPatternArray[myPatternLength - 1 - index] == lastChar) break;
          }
          step = myPatternLength - index - 1;
          mySearchTable[lastChar] = step;
        }

        start += step;
      }
    }
    return -1;
  }

  private char normalizedCharAt(@NotNull CharSequence text, char @Nullable [] textArray, int index) {
    char lastChar = textArray != null ? textArray[index] : text.charAt(index);
    if (myCaseSensitive) {
      return lastChar;
    }
    return myLowercaseTransform ? Strings.toLowerCase(lastChar) : Strings.toUpperCase(lastChar);
  }

  private boolean isSameChar(char charInPattern, char charInText) {
    boolean sameChar = charInPattern == charInText;
    if (!sameChar && !myCaseSensitive) {
      return Strings.charsEqualIgnoreCase(charInPattern, charInText);
    }
    return sameChar;
  }

  @Override
  public String toString() {
    return "pattern " + myPattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StringSearcher searcher = (StringSearcher)o;

    if (myCaseSensitive != searcher.myCaseSensitive) return false;
    if (myLowercaseTransform != searcher.myLowercaseTransform) return false;
    if (myForwardDirection != searcher.myForwardDirection) return false;
    if (myJavaIdentifier != searcher.myJavaIdentifier) return false;
    if (myHandleEscapeSequences != searcher.myHandleEscapeSequences) return false;
    return myPattern.equals(searcher.myPattern);
  }

  @Override
  public int hashCode() {
    int result = myPattern.hashCode();
    result = 31 * result + (myCaseSensitive ? 1 : 0);
    result = 31 * result + (myLowercaseTransform ? 1 : 0);
    result = 31 * result + (myForwardDirection ? 1 : 0);
    result = 31 * result + (myJavaIdentifier ? 1 : 0);
    result = 31 * result + (myHandleEscapeSequences ? 1 : 0);
    return result;
  }
}
