// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.zmlx.hg4idea.action;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.VcsLogSingleCommitAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;

public abstract class HgLogSingleCommitAction extends VcsLogSingleCommitAction<HgRepository> {

  @NotNull
  @Override
  protected AbstractRepositoryManager<HgRepository> getRepositoryManager(@NotNull Project project) {
    return project.getService(HgRepositoryManager.class);
  }

  @Nullable
  @Override
  protected HgRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return getRepositoryManager(project).getRepositoryForRootQuick(root);
  }

}
