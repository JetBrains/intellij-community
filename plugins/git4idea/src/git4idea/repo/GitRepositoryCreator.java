// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryCreator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class GitRepositoryCreator implements VcsRepositoryCreator {
  @Override
  public @Nullable Repository createRepositoryIfValid(@NotNull Project project, @NotNull VirtualFile root, @NotNull Disposable parentDisposable) {
    VirtualFile gitDir = GitUtil.findGitDir(root);
    return gitDir == null ? null : GitRepositoryImpl.createInstance(root, gitDir, project, parentDisposable);
  }

  @Override
  public @NotNull VcsKey getVcsKey() {
    return GitVcs.getKey();
  }
}
