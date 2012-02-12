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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;

import java.util.List;

/**
 * @author irengrig
 *         Date: 7/6/11
 *         Time: 8:46 PM
 */
public class FragmentedContent {
  private final Document myBefore;
  private final Document myAfter;
  private final List<BeforeAfter<TextRange>> myRanges;
  private boolean myOneSide;
  private boolean myIsAddition;

  public FragmentedContent(Document before, Document after, List<BeforeAfter<TextRange>> ranges) {
    myBefore = before;
    myAfter = after;
    myRanges = ranges;
  }

  public Document getBefore() {
    return myBefore;
  }

  public Document getAfter() {
    return myAfter;
  }

  public List<BeforeAfter<TextRange>> getRanges() {
    return myRanges;
  }
  
  public int getSize() {
    return myRanges.size();
  }

  public boolean isOneSide() {
    return myOneSide;
  }

  public void setOneSide(boolean oneSide) {
    myOneSide = oneSide;
  }

  public boolean isAddition() {
    return myIsAddition;
  }

  public void setIsAddition(boolean isAddition) {
    myIsAddition = isAddition;
  }
}
