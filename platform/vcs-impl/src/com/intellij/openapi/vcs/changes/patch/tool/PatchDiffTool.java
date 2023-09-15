// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

final class PatchDiffTool implements FrameDiffTool {
  @NotNull
  @Override
  public String getName() {
    return VcsBundle.message("patch.content.viewer.name");
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return request instanceof PatchDiffRequest;
  }

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new PatchDiffViewer(context, (PatchDiffRequest)request);
  }
}
