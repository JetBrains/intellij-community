// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public class ShowDiffLineStatusAction extends LineStatusActionBase {
  @Override
  protected void doAction(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor, @Nullable Range range) {
    if (range == null) {
      //noinspection unchecked
      List<Range> ranges = (List<Range>)tracker.getRangesForLines(DiffUtil.getSelectedLines(editor));
      if (ranges == null || ranges.isEmpty()) return;

      Range start = ranges.get(0);
      Range end = ranges.get(ranges.size() - 1);
      range = new Range(start.getLine1(), end.getLine2(), start.getVcsLine1(), end.getVcsLine2());
    }
    LineStatusMarkerPopupActions.showDiff(tracker, range);
  }
}
