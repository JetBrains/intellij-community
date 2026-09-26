// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryCreator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

final class HgRepositoryCreator implements VcsRepositoryCreator {
  @Override
  public Repository createRepositoryIfValid(@NotNull Project project, @NotNull VirtualFile root, @NotNull Disposable parentDisposable) {
    return HgUtil.isHgRoot(root) ? HgRepositoryImpl.getInstance(root, project, parentDisposable) : null;
  }

  @Override
  public @NotNull VcsKey getVcsKey() {
    return HgVcs.getKey();
  }
}
