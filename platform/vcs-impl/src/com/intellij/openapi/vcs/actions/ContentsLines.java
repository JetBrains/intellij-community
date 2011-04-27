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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
* @author irengrig
*         Date: 4/27/11
*         Time: 3:42 PM
*/
public class ContentsLines {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.ContentsLines");

  private final SplittingIterator mySplittingIterator;
  private final List<Integer> myLinesStartOffsets;
  private final String myContents;
  private boolean myLineEndsFinished;

  public ContentsLines(final String contents) {
    myContents = contents;
    mySplittingIterator = new SplittingIterator(contents);
    myLinesStartOffsets = new ArrayList<Integer>();
  }

  public String getLineContents(final int number) {
    assert !myLineEndsFinished || myLinesStartOffsets.size() > number;

    // we need to know end
    if (myLineEndsFinished || (myLinesStartOffsets.size() > (number + 1))) {
      return extractCalculated(number);
    }
    while (((myLinesStartOffsets.size() - 1) < (number + 1)) && (!myLineEndsFinished) && mySplittingIterator.hasNext()) {
      final Integer nextStart = mySplittingIterator.next();
      myLinesStartOffsets.add(nextStart);
    }
    myLineEndsFinished = myLinesStartOffsets.size() < (number + 1);
    return extractCalculated(number);
  }

  private String extractCalculated(int number) {
    try {
    String text = myContents.substring(myLinesStartOffsets.get(number),
                                       (number + 1 >= myLinesStartOffsets.size())
                                       ? myContents.length()
                                       : myLinesStartOffsets.get(number + 1));
    text = text.endsWith("\r\n") ? text.substring(0, text.length() - 2) : text;
    text = (text.endsWith("\r") || text.endsWith("\n")) ? text.substring(0, text.length() - 1) : text;
    return text;
    } catch (IndexOutOfBoundsException e) {
      LOG.error("Loaded contents lines: " + StringUtil.getLineBreakCount(myContents), e);
      throw e;
    }
  }

  public boolean isLineEndsFinished() {
    return myLineEndsFinished;
  }

  public int getKnownLinesNumber() {
    return myLineEndsFinished ? myLinesStartOffsets.size() : -1;
  }
}
