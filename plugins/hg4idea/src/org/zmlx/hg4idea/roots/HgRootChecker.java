// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.roots;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.nio.file.Files;

final class HgRootChecker extends VcsRootChecker {
  @Override
  public boolean isRoot(@NotNull VirtualFile file) {
    return file.findChild(HgUtil.DOT_HG) != null;
  }

  @Override
  public boolean validateRoot(@NotNull VirtualFile file) {
    return Files.exists(file.toNioPath().resolve(HgUtil.DOT_HG));
  }

  @Override
  public @NotNull VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }

  @Override
  public boolean isVcsDir(@NotNull String dirName) {
    return dirName.equalsIgnoreCase(HgUtil.DOT_HG);
  }
}
