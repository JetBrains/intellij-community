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

  public FindBlock(String[] prevVersion, Block currentVersion) {
    myCurrentVersion = currentVersion;
    myLines = prevVersion;
  }

  public Block getBlockInThePrevVersion() {
    int startLine = myCurrentVersion.getStart();
    int endLine = myCurrentVersion.getEnd();

    Diff.Change change = Diff.buildChangesSomehow(myLines, myCurrentVersion.getSource());
    while (change != null) {
      int startLine1 = change.line0;
      int startLine2 = change.line1;
      int endLine1 = startLine1 + change.deleted;
      int endLine2 = startLine2 + change.inserted;

      int shiftStart = startLine2 - startLine1;
      int shiftEnd = endLine2 - endLine1;

      if (startLine2 <= myCurrentVersion.getStart()) {
        startLine = myCurrentVersion.getStart() - shiftStart;
      }

      if (endLine2 <= myCurrentVersion.getStart()) {
        startLine = myCurrentVersion.getStart() - shiftEnd;
      }

      if (startLine2 < myCurrentVersion.getEnd()) {
        endLine = myCurrentVersion.getEnd() - shiftEnd;
      }

      change = change.link;
    }

    if (endLine > myLines.length) {
      endLine = myLines.length;
    }
    if (startLine < 0) startLine = 0;
    if (endLine < startLine) endLine = startLine;

    return new Block(myLines, startLine, endLine);
  }
}
