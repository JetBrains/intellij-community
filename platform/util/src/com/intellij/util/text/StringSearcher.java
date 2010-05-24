/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.Arrays;

/**
 *
 */
public class StringSearcher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.text.StringSearcher");

  private final String myPattern;
  private final char[] myPatternArray;
  private final int myPatternLength;
  private final int[] mySearchTable = new int[128];
  private final boolean myCaseSensitive;
  private final boolean myForwardDirection;
  private final boolean myJavaIdentifier;

  public int getPatternLength() {
    return myPatternLength;
  }

  public StringSearcher(@NotNull String pattern, boolean caseSensitive, boolean forwardDirection) {
    LOG.assertTrue(pattern.length() > 0);
    myPattern = pattern;
    myCaseSensitive = caseSensitive;
    myForwardDirection = forwardDirection;
    myPatternArray = myCaseSensitive ? myPattern.toCharArray() : myPattern.toLowerCase().toCharArray();
    myPatternLength = myPatternArray.length;
    Arrays.fill(mySearchTable, -1);
    myJavaIdentifier = pattern.length() == 0 ||
                       Character.isJavaIdentifierPart(pattern.charAt(0)) &&
                       Character.isJavaIdentifierPart(pattern.charAt(pattern.length() - 1));
  }

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

  public int scan(CharSequence text) {
    return scan(text,0,text.length());
  }
  
  public int scan(CharSequence text, int _start, int _end) {
    LOG.assertTrue(_start <= _end, _start - _end);
    LOG.assertTrue(_end <= text.length(), text.length() - _end);
    if (myForwardDirection) {
      int start = _start;
      int end = _end - myPatternLength;

      while (start <= end) {
        int i = myPatternLength - 1;
        char lastChar = text.charAt(start + i);
        if (!myCaseSensitive) {
          lastChar = StringUtil.toLowerCase(lastChar);
        }
        if (myPatternArray[i] == lastChar) {
          i--;
          while (i >= 0) {
            char c = text.charAt(start + i);
            if (!myCaseSensitive) {
              c = StringUtil.toLowerCase(c);
            }
            if (myPatternArray[i] != c) break;
            i--;
          }
          if (i < 0) return start;
        }

        int step = 0 <= lastChar && lastChar < 128 ? mySearchTable[lastChar] : 1;

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
      int end = text.length() - myPatternLength + 1;
      while (start <= end) {
        int i = myPatternLength - 1;
        char lastChar = text.charAt(text.length() - (start + i));
        if (!myCaseSensitive) {
          lastChar = StringUtil.toLowerCase(lastChar);
        }
        if (myPatternArray[myPatternLength - 1 - i] == lastChar) {
          i--;
          while (i >= 0) {
            char c = text.charAt(text.length() - (start + i));
            if (!myCaseSensitive) {
              c = StringUtil.toLowerCase(c);
            }
            if (myPatternArray[myPatternLength - 1 - i] != c) break;
            i--;
          }
          if (i < 0) return text.length() - start - myPatternLength + 1;
        }

        int step = 0 <= lastChar && lastChar < 128 ? mySearchTable[lastChar] : 1;

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
   * @param text
   * @param startOffset
   * @param endOffset
   * @return
   */
  public int scan(char[] text, int startOffset, int endOffset){
    final int res = scan(new CharArrayCharSequence(text),startOffset, endOffset);
    return res >= 0 ? res: -1;
  }
}
