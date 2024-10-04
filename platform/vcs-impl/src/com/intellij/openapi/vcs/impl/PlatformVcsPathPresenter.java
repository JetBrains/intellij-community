// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class PlatformVcsPathPresenter extends VcsPathPresenter {
  @Override
  @NotNull
  public String getPresentableRelativePathFor(final VirtualFile file) {
    return FileUtil.toSystemDependentName(file.getPath());
  }

  @Override
  @NotNull
  public String getPresentableRelativePath(final ContentRevision fromRevision, final ContentRevision toRevision) {
    FilePath path = toRevision.getFile();
    FilePath originalPath = fromRevision.getFile();
    return getPresentableRelativePath(path, originalPath);
  }

  public static @NlsSafe @NotNull String getPresentableRelativePath(@NotNull FilePath path, @NotNull FilePath originalPath) {
    String relativePath = RelativePathCalculator.computeRelativePath(path.getPath(), originalPath.getPath(), false);
    return FileUtilRt.toSystemDependentName(relativePath);
  }
}
