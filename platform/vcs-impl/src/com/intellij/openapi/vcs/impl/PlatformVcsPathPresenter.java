// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
public class PlatformVcsPathPresenter extends VcsPathPresenter {
  @Override
  @NotNull
  public String getPresentableRelativePathFor(final VirtualFile file) {
    return FileUtil.toSystemDependentName(file.getPath());
  }

  @Override
  @NotNull
  public String getPresentableRelativePath(final ContentRevision fromRevision, final ContentRevision toRevision) {
    RelativePathCalculator calculator = new RelativePathCalculator(toRevision.getFile().getPath(), fromRevision.getFile().getPath());

    final String result = calculator.execute();
    return result.replace("/", File.separator);
  }
}
