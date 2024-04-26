// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.roots;

import com.intellij.openapi.vcs.roots.VcsIntegrationEnabler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgInit;
import org.zmlx.hg4idea.util.HgUtil;

public class HgIntegrationEnabler extends VcsIntegrationEnabler {

  public HgIntegrationEnabler(@NotNull HgVcs vcs, @Nullable VirtualFile targetDirectory) {
    super(vcs, targetDirectory);
  }

  @Override
  protected boolean initOrNotifyError(final @NotNull VirtualFile directory) {
    if (HgInit.createRepository(myProject, directory)) {
      refreshVcsDir(directory, HgUtil.DOT_HG);
      return true;
    }
    return false;
  }
}
