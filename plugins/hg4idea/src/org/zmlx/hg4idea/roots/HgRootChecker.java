// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.roots;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;

final class HgRootChecker extends VcsRootChecker {
  @Override
  public boolean isRoot(@NotNull String path) {
    return new File(path, HgUtil.DOT_HG).exists();
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }

  @Override
  public boolean isVcsDir(@NotNull String dirName) {
    return dirName.equalsIgnoreCase(HgUtil.DOT_HG);
  }
}
