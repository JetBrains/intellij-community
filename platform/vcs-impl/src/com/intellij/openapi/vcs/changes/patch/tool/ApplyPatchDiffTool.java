// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ApplyPatchDiffTool implements FrameDiffTool {
  @Override
  public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new MyApplyPatchViewer(context, (ApplyPatchDiffRequest)request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return request instanceof ApplyPatchDiffRequest;
  }

  @Override
  public @NotNull String getName() {
    return VcsBundle.message("patch.apply.somehow.diff.name");
  }

  private static class MyApplyPatchViewer extends ApplyPatchViewer implements DiffViewer {
    MyApplyPatchViewer(@NotNull DiffContext context, @NotNull ApplyPatchDiffRequest request) {
      super(context, request);
    }

    @Override
    public @NotNull ToolbarComponents init() {
      initPatchViewer();

      ToolbarComponents components = new ToolbarComponents();
      components.statusPanel = getStatusPanel();
      components.toolbarActions = createToolbarActions();

      return components;
    }
  }
}
