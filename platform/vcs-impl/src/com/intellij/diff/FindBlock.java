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
package com.intellij.diff;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;

/**
 * author: lesya
 */
public class FindBlock {

  private final Block myCurrentVersion;
  private final Block myResult;

  public FindBlock(String[] prevVersion, Block currentVersion) {
    myResult = new Block(prevVersion, currentVersion.getStart(), currentVersion.getEnd());
    myCurrentVersion = currentVersion;
  }

  public FindBlock(String prevVersion, Block currentVersion) {
    this(LineTokenizer.tokenize(prevVersion.toCharArray(), false), currentVersion);
  }


  public Block getBlockInThePrevVersion() throws FilesTooBigForDiffException {

    Diff.Change change = Diff.buildChanges(myResult.getSource(), myCurrentVersion.getSource());
    while (change != null) {
      shiftIndices(change.line1, change.line1, change.line0);
      shiftIndices(change.line1, change.line1 + change.inserted, change.line0 + change.deleted);
      change = change.link;
    }

    if (myResult.getEnd() >= myResult.getSource().length){
      myResult.setEnd(myResult.getSource().length - 1);
    }

    return myResult;
  }

  private void shiftIndices(int firstChangeIndex,int line1, int line0) {
    int shift = line1 - line0;

    if (line1 <= myCurrentVersion.getStart()) {
      myResult.setStart(myCurrentVersion.getStart() - shift);
    }

    if (firstChangeIndex <= myCurrentVersion.getEnd()) {
      myResult.setEnd(myCurrentVersion.getEnd() - shift);
    }
  }
}
