// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.impl.DiffToolSubstitutor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalChangeListDiffTool implements FrameDiffTool, DiffToolSubstitutor {
  public static final Key<Boolean> ALLOW_EXCLUDE_FROM_COMMIT = Key.create("Diff.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT");

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    LocalChangeListDiffRequest localRequest = (LocalChangeListDiffRequest)request;
    return new SimpleLocalChangeListDiffViewer(context, localRequest);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof LocalChangeListDiffRequest)) return false;
    LocalChangeListDiffRequest localRequest = (LocalChangeListDiffRequest)request;
    return localRequest.getLineStatusTracker() instanceof PartialLocalLineStatusTracker;
  }

  @NotNull
  @Override
  public String getName() {
    return SimpleDiffTool.INSTANCE.getName();
  }

  @Nullable
  @Override
  public DiffTool getReplacement(@NotNull DiffTool tool, @NotNull DiffContext context, @NotNull DiffRequest request) {
    if (tool != SimpleDiffTool.INSTANCE) return null;
    if (!canShow(context, request)) return null;
    return this;
  }
}
