// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.editor.GutterMarkPreprocessor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.xdebugger.XDebuggerUtil.INLINE_BREAKPOINTS_KEY;

final class BreakpointGutterIconMerge implements GutterMarkPreprocessor {
  @Override
  public @NotNull List<GutterMark> processMarkers(@NotNull List<GutterMark> marks) {
    // In general, it seems ok to merge breakpoints because they are drawn one over another in the new UI.
    // But we disable it in the old mode just for ease of regressions debugging.
    if (!Registry.is(INLINE_BREAKPOINTS_KEY)) return marks;

    var breakpointCount = ContainerUtil.count(marks, m -> m instanceof BreakpointGutterIconRenderer);
    if (breakpointCount <= 1) {
      return marks;
    }

    var newMarks = new ArrayList<GutterMark>(marks.size() - breakpointCount + 1);
    var breakpoints = new ArrayList<XBreakpointProxy>(breakpointCount);
    var breakpointMarkPosition = -1;
    for (GutterMark mark : marks) {
      assert !(mark instanceof MultipleBreakpointGutterIconRenderer) : "they are not expected to be created before processing";
      if (mark instanceof BreakpointGutterIconRenderer singleBreakpointMark) {
        breakpoints.add(singleBreakpointMark.getBreakpoint());
        breakpointMarkPosition = newMarks.size();
        continue;
      }

      newMarks.add(mark);
    }
    newMarks.add(breakpointMarkPosition, new MultipleBreakpointGutterIconRenderer(breakpoints));

    return newMarks;
  }
}
