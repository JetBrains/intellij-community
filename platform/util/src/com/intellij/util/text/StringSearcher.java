/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

public class StringSearcher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.text.StringSearcher");

  private final String myPattern;
  private final char[] myPatternArray;
  private final int myPatternLength;
  private final int[] mySearchTable = new int[128];
  private final boolean myCaseSensitive;
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
    LOG.assertTrue(!pattern.isEmpty());
    myPattern = pattern;
    myCaseSensitive = caseSensitive;
    myForwardDirection = forwardDirection;
    myPatternArray = myCaseSensitive ? myPattern.toCharArray() : myPattern.toLowerCase(Locale.US).toCharArray();
    myPatternLength = myPatternArray.length;
    Arrays.fill(mySearchTable, -1);
    myJavaIdentifier = lookForJavaIdentifiersOnlyIfPossible &&
                       (pattern.isEmpty() ||
                       Character.isJavaIdentifierPart(pattern.charAt(0)) &&
                       Character.isJavaIdentifierPart(pattern.charAt(pattern.length() - 1)));
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

  public boolean isForwardDirection() {
    return myForwardDirection;
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

  public int scan(@NotNull CharSequence text, @Nullable char[] textArray, int _start, int _end) {
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
        return StringUtil.indexOf(text, myPatternArray[0], _start, _end, myCaseSensitive);
      }
      int start = _start;
      int end = _end - myPatternLength;

      while (start <= end) {
        int i = myPatternLength - 1;
        char lastChar = textArray != null ? textArray[start + i] : text.charAt(start + i);
        if (!myCaseSensitive) {
          lastChar = StringUtil.toLowerCase(lastChar);
        }
        if (myPatternArray[i] == lastChar) {
          i--;
          while (i >= 0) {
            char c = textArray != null ? textArray[start + i] : text.charAt(start + i);
            if (!myCaseSensitive) {
              c = StringUtil.toLowerCase(c);
            }
            if (myPatternArray[i] != c) break;
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
      return -1;
    }
    else {
      int start = 1;
      int end = _end + 1;
      while (start <= end - myPatternLength + 1) {
        int i = myPatternLength - 1;
        char lastChar = textArray != null ? textArray[end - (start + i)] : text.charAt(end - (start + i));
        if (!myCaseSensitive) {
          lastChar = StringUtil.toLowerCase(lastChar);
        }
        if (myPatternArray[myPatternLength - 1 - i] == lastChar) {
          i--;
          while (i >= 0) {
            char c = textArray != null ? textArray[end - (start + i)] : text.charAt(end - (start + i));
            if (!myCaseSensitive) {
              c = StringUtil.toLowerCase(c);
            }
            if (myPatternArray[myPatternLength - 1 - i] != c) break;
            i--;
          }
          if (i < 0) return end - start - myPatternLength + 1;
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
      return -1;
    }
  }

  /**
   * @deprecated Use {@link #scan(CharSequence)} instead
   */
  public int scan(char[] text, int startOffset, int endOffset){
    final int res = scan(new CharArrayCharSequence(text),text, startOffset, endOffset);
    return res >= 0 ? res: -1;
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
    if (myForwardDirection != searcher.myForwardDirection) return false;
    if (myJavaIdentifier != searcher.myJavaIdentifier) return false;
    if (myHandleEscapeSequences != searcher.myHandleEscapeSequences) return false;
    return myPattern.equals(searcher.myPattern);
  }

  @Override
  public int hashCode() {
    int result = myPattern.hashCode();
    result = 31 * result + (myCaseSensitive ? 1 : 0);
    result = 31 * result + (myForwardDirection ? 1 : 0);
    result = 31 * result + (myJavaIdentifier ? 1 : 0);
    result = 31 * result + (myHandleEscapeSequences ? 1 : 0);
    return result;
  }
}
