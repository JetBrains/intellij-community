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

  public ApplyPatchStatus apply(final List<String> lines) throws ApplyPatchException {
    List<String> originalLines = new ArrayList<String>(lines);
    try {
      return tryApply(lines, false);
    }
    catch(ApplyPatchException ex) {
      lines.clear();
      lines.addAll(originalLines);
      return tryApply(lines, true);
    }
  }

  private ApplyPatchStatus tryApply(final List<String> lines, boolean acceptPartial) throws ApplyPatchException {
    ApplyPatchStatus result = null;
    int curLine = findStartLine(lines);
    for(PatchLine line: myLines) {
      final String patchLineText = line.getText();
      switch (line.getType()) {
        case CONTEXT:
          checkContextMismatch(lines, curLine, patchLineText);
          curLine++;
          break;

        case ADD:
          if (curLine < lines.size() && lines.get(curLine).equals(patchLineText) && acceptPartial) {
            result = ApplyPatchStatus.and(result, ApplyPatchStatus.ALREADY_APPLIED);
          }
          else {
            lines.add(curLine, patchLineText);
            result = ApplyPatchStatus.and(result, ApplyPatchStatus.SUCCESS);
          }
          curLine++;
          break;

        case REMOVE:
          if (curLine >= lines.size() || !patchLineText.equals(lines.get(curLine))) {
            if (acceptPartial) {
              // we'll get a context mismatch exception later if it's actually a conflict and not an already applied line
              result = ApplyPatchStatus.and(result, ApplyPatchStatus.ALREADY_APPLIED);
            }
            else {
              checkContextMismatch(lines, curLine, patchLineText);
            }
          }
          else {
            lines.remove(curLine);
            result = ApplyPatchStatus.and(result, ApplyPatchStatus.SUCCESS);
          }
          break;
      }
    }
    if (result != null) {
      return result;
    }
    return ApplyPatchStatus.SUCCESS;
  }

  private static void checkContextMismatch(final List<String> lines, final int curLine, final String patchLineText) throws ApplyPatchException {
    if (curLine >= lines.size()) {
      throw new ApplyPatchException("Unexpected end of document. Expected line:\n" + patchLineText);
    }
    if (!patchLineText.equals(lines.get(curLine))) {
      throw new ApplyPatchException("Context mismatch. Expected line:\n" + patchLineText + "\nFound line:\n" + lines.get(curLine));
    }
  }

  private int findStartLine(final List<String> lines) throws ApplyPatchException {
    int totalContextLines = countContextLines();
    if (getLinesProcessingContext(lines, myStartLineBefore) == totalContextLines) {
      return myStartLineBefore;
    }
    int maxContextStartLine = -1;
    int maxContextLines = 0;
    for(int i=0;i< lines.size(); i++) {
      int contextLines = getLinesProcessingContext(lines, i);
      if (contextLines == totalContextLines) {
        return i;
      }
      if (contextLines > maxContextLines) {
        maxContextLines = contextLines;
        maxContextStartLine = i;
      }
    }
    if (maxContextLines < 2) {
      throw new ApplyPatchException("couldn't find context");
    }
    return maxContextStartLine;
  }

  private int countContextLines() {
    int count = 0;
    for(PatchLine line: myLines) {
      if (line.getType() == PatchLine.Type.CONTEXT || line.getType() == PatchLine.Type.REMOVE) {
        count++;
      }
    }
    return count;
  }

  private int getLinesProcessingContext(final List<String> lines, int startLine) {
    int count = 0;
    for(PatchLine line: myLines) {
      PatchLine.Type type = line.getType();
      if (type == PatchLine.Type.REMOVE || type == PatchLine.Type.CONTEXT) {
        // TODO: smarter algorithm (search outward from non-context lines)
        if (startLine >= lines.size() || !line.getText().equals(lines.get(startLine))) {
          return count;
        }
        count++;
        startLine++;
      }
    }
    return count;
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
