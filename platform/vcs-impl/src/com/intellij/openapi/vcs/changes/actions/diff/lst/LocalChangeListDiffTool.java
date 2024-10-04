// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.DiffToolType;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.impl.DiffToolSubstitutor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LocalChangeListDiffTool {
  public static final Key<Boolean> ALLOW_EXCLUDE_FROM_COMMIT = Key.create("Diff.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT");

  public static class Simple extends Base {
    public Simple() {
      super(SimpleDiffTool.INSTANCE);
    }

    @NotNull
    @Override
    public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new SimpleLocalChangeListDiffViewer(context, (LocalChangeListDiffRequest)request);
    }
  }

  public static class Unified extends Base {
    public Unified() {
      super(UnifiedDiffTool.INSTANCE);
    }

    @NotNull
    @Override
    public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new UnifiedLocalChangeListDiffViewer(context, (LocalChangeListDiffRequest)request);
    }

    @Override
    public @NotNull DiffToolType getToolType() {
      return DiffToolType.Unified.INSTANCE;
    }
  }

  private static abstract class Base implements FrameDiffTool, DiffToolSubstitutor {
    @NotNull private final FrameDiffTool myReplacement;

    protected Base(@NotNull FrameDiffTool replacement) {
      myReplacement = replacement;
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      if (!(request instanceof LocalChangeListDiffRequest localRequest)) return false;
      return localRequest.getLineStatusTracker() instanceof PartialLocalLineStatusTracker;
    }

    @NotNull
    @Override
    public String getName() {
      return myReplacement.getName();
    }

    @Nullable
    @Override
    public DiffTool getReplacement(@NotNull DiffTool tool, @NotNull DiffContext context, @NotNull DiffRequest request) {
      if (tool != myReplacement) return null;
      if (!canShow(context, request)) return null;
      return this;
    }
  }
}
