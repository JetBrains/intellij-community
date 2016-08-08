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
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.PatchLine;

import java.util.ArrayList;
import java.util.List;

public class ApplyPatchHunk {
  private final PatchHunk myHunk;

  public ApplyPatchHunk(final PatchHunk hunk) {
    myHunk = hunk;
  }
  
  public ApplyPatchStatus apply(final List<String> lines) throws ApplyPatchException {
    List<String> originalLines = new ArrayList<>(lines);
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
    final List<PatchLine> hunkLines = myHunk.getLines();
    ApplyPatchStatus result = null;
    int curLine = findStartLine(hunkLines, lines);
    for(PatchLine line: hunkLines) {
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

  private int findStartLine(final List<PatchLine> hunkLines, final List<String> lines) throws ApplyPatchException {
    int totalContextLines = countContextLines(hunkLines);
    final int startLineBefore = myHunk.getStartLineBefore();
    if (getLinesProcessingContext(hunkLines, lines, startLineBefore) == totalContextLines) {
      return startLineBefore;
    }
    int maxContextStartLine = -1;
    int maxContextLines = 0;
    for(int i=0;i< lines.size(); i++) {
      int contextLines = getLinesProcessingContext(hunkLines, lines, i);
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

  private int countContextLines(final List<PatchLine> hunkLines) {
    int count = 0;
    for(PatchLine line: hunkLines) {
      if (line.getType() == PatchLine.Type.CONTEXT || line.getType() == PatchLine.Type.REMOVE) {
        count++;
      }
    }
    return count;
  }

  private int getLinesProcessingContext(final List<PatchLine> hunkLines, final List<String> lines, int startLine) {
    int count = 0;
    for(PatchLine line: hunkLines) {
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
}
