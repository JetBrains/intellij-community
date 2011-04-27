/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import java.util.Iterator;

/**
* @author irengrig
*         Date: 4/27/11
*         Time: 3:42 PM
*/
public class SplittingIterator implements Iterator<Integer> {
  private final String myContents;
  // always at the beginning of the _next_ line
  private int myOffset;

  public SplittingIterator(final String contents) {
    myContents = contents;
    myOffset = 0;
  }

  @Override
  public boolean hasNext() {
    return myOffset < myContents.length();
  }

  @Override
  public Integer next() {
    final int start = myOffset;
    while (myOffset < myContents.length()) {
      // \r, \n, or \r\n
      final char c = myContents.charAt(myOffset);
      if ('\n' == c) {
        ++myOffset;
        break;
      }
      else if ('\r' == c) {
        if (myOffset + 1 == myContents.length()) {
          // at the end
          ++myOffset;
          break;
        }
        else {
          myOffset += (('\n' == myContents.charAt(myOffset + 1)) ? 2 : 1);
          break;
        }
      }
      ++myOffset;
    }

    return start;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
