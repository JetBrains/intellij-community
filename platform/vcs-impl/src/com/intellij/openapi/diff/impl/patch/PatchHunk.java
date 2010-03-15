/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 20:20:15
 */
package com.intellij.openapi.diff.impl.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PatchHunk {
  private final int myStartLineBefore;
  private final int myEndLineBefore;
  private final int myStartLineAfter;
  private final int myEndLineAfter;
  private final List<PatchLine> myLines = new ArrayList<PatchLine>();

  public PatchHunk(final int startLineBefore, final int endLineBefore, final int startLineAfter, final int endLineAfter) {
    myStartLineBefore = startLineBefore;
    myEndLineBefore = endLineBefore;
    myStartLineAfter = startLineAfter;
    myEndLineAfter = endLineAfter;
  }

  public int getStartLineBefore() {
    return myStartLineBefore;
  }

  public int getEndLineBefore() {
    return myEndLineBefore;
  }

  public int getStartLineAfter() {
    return myStartLineAfter;
  }

  public int getEndLineAfter() {
    return myEndLineAfter;
  }

  public void addLine(final PatchLine line) {
    myLines.add(line);
  }

  public List<PatchLine> getLines() {
    return Collections.unmodifiableList(myLines);
  }

  public boolean isNewContent() {
    return myStartLineBefore == -1 && myEndLineBefore == -1;
  }

  public boolean isDeletedContent() {
    return myStartLineAfter == -1 && myEndLineAfter == -1;
  }

  public String getText() {
    StringBuilder builder = new StringBuilder();
    for(PatchLine line: myLines) {
      builder.append(line.getText()).append("\n");
    }
    return builder.toString();
  }

  public boolean isNoNewLineAtEnd() {
    if (myLines.size() == 0) {
      return false;
    }
    return myLines.get(myLines.size()-1).isSuppressNewLine();
  }
}
