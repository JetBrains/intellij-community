// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffToolType;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

final class PatchDiffTool {
  static class Unified implements FrameDiffTool {
    @Override
    public @NotNull String getName() {
      return VcsBundle.message("patch.content.viewer.name");
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return request instanceof PatchDiffRequest;
    }

    @Override
    public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new PatchDiffViewer(context, (PatchDiffRequest)request);
    }

    @Override
    public @NotNull DiffToolType getToolType() {
      return DiffToolType.Unified.INSTANCE;
    }
  }

  static class SideBySide implements FrameDiffTool {
    @Override
    public @NotNull String getName() {
      return VcsBundle.message("patch.content.viewer.side.by.side.name");
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return request instanceof PatchDiffRequest;
    }

    @Override
    public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      PatchDiffRequest patchRequest = (PatchDiffRequest)request;
      TextFilePatch patch = patchRequest.getPatch();
      if (patch.isDeletedFile() || patch.isNewFile()) {
        return new PatchDiffViewer(context, patchRequest);
      }
      return new SideBySidePatchDiffViewer(context, patchRequest);
    }
  }
}
