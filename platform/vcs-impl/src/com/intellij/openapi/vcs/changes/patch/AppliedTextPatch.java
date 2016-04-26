 /*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.util.LineRange;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus.NOT_APPLIED;


public class AppliedTextPatch {
  @NotNull private final List<AppliedSplitPatchHunk> mySplitPatchHunkList;

  public enum HunkStatus {ALREADY_APPLIED, EXACTLY_APPLIED, NOT_APPLIED}

  public AppliedTextPatch(@NotNull List<AppliedSplitPatchHunk> splitPatchHunkList) {
    mySplitPatchHunkList = ContainerUtil.sorted(splitPatchHunkList,
                                                (o1, o2) -> Integer.compare(o1.getLineRangeBefore().start, o2.getLineRangeBefore().start));
  }

  @NotNull
  public List<AppliedSplitPatchHunk> getHunks() {
    return mySplitPatchHunkList;
  }

  public static class AppliedSplitPatchHunk {
    @NotNull private final HunkStatus myStatus;

    @NotNull private final List<String> myContextBefore;
    @NotNull private final List<String> myContextAfter;

    @NotNull private final List<String> myDeletedLines;
    @NotNull private final List<String> myInsertedLines;

    private final int myAppliedToLinesStart;
    private final int myAppliedToLinesEnd;

    private final int myStartLineBefore;
    private final int myStartLineAfter;

    public AppliedSplitPatchHunk(@NotNull GenericPatchApplier.SplitHunk splitHunk,
                                 int startLineApplied,
                                 int endLineApplied,
                                 @NotNull HunkStatus status) {
      myStatus = status;
      myAppliedToLinesStart = startLineApplied;
      myAppliedToLinesEnd = endLineApplied;

      myStartLineBefore = splitHunk.getStartLineBefore();
      myStartLineAfter = splitHunk.getStartLineAfter();

      myContextBefore = splitHunk.getContextBefore();
      myContextAfter = splitHunk.getContextAfter();

      myDeletedLines = new ArrayList<>();
      myInsertedLines = new ArrayList<>();
      for (BeforeAfter<List<String>> step : splitHunk.getPatchSteps()) {
        myDeletedLines.addAll(step.getBefore());
        myInsertedLines.addAll(step.getAfter());
      }
    }

    /*
     * Lines that hunk can be applied to
     */
    public LineRange getAppliedTo() {
      if (myStatus == NOT_APPLIED) return null;
      return new LineRange(myAppliedToLinesStart, myAppliedToLinesEnd);
    }

    /*
     * Hunk lines (including context) in base document
     */
    @NotNull
    public LineRange getLineRangeBefore() {
      int start = myStartLineBefore;
      return new LineRange(start, start + myContextBefore.size() + myDeletedLines.size() + myContextAfter.size());
    }

    /*
     * Hunk lines (including context) in originally patched document
     */
    @NotNull
    public LineRange getLineRangeAfter() {
      int start = myStartLineAfter;
      return new LineRange(start, start + myContextBefore.size() + myInsertedLines.size() + myContextAfter.size());
    }

    @NotNull
    public HunkStatus getStatus() {
      return myStatus;
    }

    @NotNull
    public List<String> getContextBefore() {
      return myContextBefore;
    }

    @NotNull
    public List<String> getContextAfter() {
      return myContextAfter;
    }

    @NotNull
    public List<String> getDeletedLines() {
      return myDeletedLines;
    }

    @NotNull
    public List<String> getInsertedLines() {
      return myInsertedLines;
    }
  }
}
