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

import com.intellij.util.diff.Diff;

/**
 * author: lesya
 */
class FindBlock {
  private final Block myCurrentVersion;
  private final String[] myLines;

  private int startLine;
  private int endLine;

  public FindBlock(String[] prevVersion, Block currentVersion) {
    myCurrentVersion = currentVersion;

    myLines = prevVersion;
    startLine = currentVersion.getStart();
    endLine = currentVersion.getEnd();
  }

  public Block getBlockInThePrevVersion() {
    Diff.Change change = Diff.buildChangesSomehow(myLines, myCurrentVersion.getSource());
    while (change != null) {
      shiftIndices(change.line1, change.line1, change.line0);
      shiftIndices(change.line1, change.line1 + change.inserted, change.line0 + change.deleted);
      change = change.link;
    }

    if (endLine > myLines.length) {
      endLine = myLines.length;
    }
    if (startLine < 0) startLine = 0;
    if (endLine < startLine) endLine = startLine;

    return new Block(myLines, startLine, endLine);
  }

  private void shiftIndices(int firstChangeIndex,int line1, int line0) {
    int shift = line1 - line0;

    if (line1 <= myCurrentVersion.getStart()) {
      startLine = myCurrentVersion.getStart() - shift;
    }

    if (firstChangeIndex < myCurrentVersion.getEnd()) {
      endLine = myCurrentVersion.getEnd() - shift;
    }
  }
}
