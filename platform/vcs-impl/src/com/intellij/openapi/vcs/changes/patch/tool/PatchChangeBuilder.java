/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.tools.fragmented.LineNumberConvertor;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.AppliedSplitPatchHunk;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class PatchChangeBuilder {
  @NotNull private final StringBuilder myBuilder = new StringBuilder();
  @NotNull private final List<Hunk> myHunks = new ArrayList<>();
  @NotNull private final LineNumberConvertor.Builder myConvertor = new LineNumberConvertor.Builder();
  @NotNull private final TIntArrayList myChangedLines = new TIntArrayList();

  private int totalLines = 0;

  public void exec(@NotNull List<AppliedSplitPatchHunk> splitHunks) {
    int lastBeforeLine = -1;
    for (AppliedSplitPatchHunk hunk : splitHunks) {
      List<String> contextBefore = hunk.getContextBefore();
      List<String> contextAfter = hunk.getContextAfter();

      LineRange beforeRange = hunk.getLineRangeBefore();
      LineRange afterRange = hunk.getLineRangeAfter();

      int overlappedContext = 0;
      if (lastBeforeLine != -1) {
        if (lastBeforeLine >= beforeRange.start) {
          overlappedContext = lastBeforeLine - beforeRange.start + 1;
        }
        else if (lastBeforeLine < beforeRange.start - 1) {
          appendSeparator();
        }
      }

      List<String> trimContext = contextBefore.subList(overlappedContext, contextBefore.size());
      addContext(trimContext, beforeRange.start + overlappedContext, afterRange.start + overlappedContext);


      int deletion = totalLines;
      appendLines(hunk.getDeletedLines());
      int insertion = totalLines;
      appendLines(hunk.getInsertedLines());
      int hunkEnd = totalLines;

      myConvertor.put1(deletion, beforeRange.start + contextBefore.size(), insertion - deletion);
      myConvertor.put2(insertion, afterRange.start + contextBefore.size(), hunkEnd - insertion);


      addContext(contextAfter, beforeRange.end - contextAfter.size(), afterRange.end - contextAfter.size());
      lastBeforeLine = beforeRange.end - 1;


      LineRange deletionRange = new LineRange(deletion, insertion);
      LineRange insertionRange = new LineRange(insertion, hunkEnd);

      myHunks.add(new Hunk(hunk.getInsertedLines(), deletionRange, insertionRange, hunk.getAppliedTo(), hunk.getStatus()));
    }
  }

  private void addContext(@NotNull List<String> context, int beforeLineNumber, int afterLineNumber) {
    myConvertor.put1(totalLines, beforeLineNumber, context.size());
    myConvertor.put2(totalLines, afterLineNumber, context.size());
    appendLines(context);
  }

  private void appendLines(@NotNull List<String> lines) {
    for (String line : lines) {
      myBuilder.append(line).append("\n");
    }
    totalLines += lines.size();
  }

  private void appendSeparator() {
    myChangedLines.add(totalLines);
    myBuilder.append("\n");
    totalLines++;
  }

  //
  // Result
  //

  @NotNull
  public CharSequence getPatchContent() {
    return myBuilder;
  }

  @NotNull
  public List<Hunk> getHunks() {
    return myHunks;
  }

  @NotNull
  public LineNumberConvertor getLineConvertor() {
    return myConvertor.build();
  }

  @NotNull
  public TIntArrayList getSeparatorLines() {
    return myChangedLines;
  }


  static class Hunk {
    @NotNull private final List<String> myInsertedLines;
    @NotNull private final LineRange myPatchDeletionRange;
    @NotNull private final LineRange myPatchInsertionRange;

    @Nullable private final LineRange myAppliedToLines;
    @NotNull private final HunkStatus myStatus;

    public Hunk(@NotNull List<String> insertedLines,
                @NotNull LineRange patchDeletionRange,
                @NotNull LineRange patchInsertionRange,
                @Nullable LineRange appliedToLines,
                @NotNull HunkStatus status) {
      myInsertedLines = insertedLines;
      myPatchDeletionRange = patchDeletionRange;
      myPatchInsertionRange = patchInsertionRange;
      myAppliedToLines = appliedToLines;
      myStatus = status;
    }

    @NotNull
    public LineRange getPatchDeletionRange() {
      return myPatchDeletionRange;
    }

    @NotNull
    public LineRange getPatchInsertionRange() {
      return myPatchInsertionRange;
    }

    @NotNull
    public HunkStatus getStatus() {
      return myStatus;
    }

    @Nullable
    public LineRange getAppliedToLines() {
      return myAppliedToLines;
    }

    @NotNull
    private List<String> getInsertedLines() {
      return myInsertedLines;
    }
  }
}
