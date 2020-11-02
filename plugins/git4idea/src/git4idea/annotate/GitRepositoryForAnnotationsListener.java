// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public final class GitRepositoryForAnnotationsListener {
  public static void registerListener(@NotNull Project project, @NotNull Disposable disposable) {
    project.getMessageBus().connect(disposable).subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
      @Override
      public void repositoryChanged(@NotNull GitRepository repository) {
        final VcsAnnotationRefresher refresher = BackgroundTaskUtil.syncPublisher(project, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED);
        refresher.dirtyUnder(repository.getRoot());
      }
    });
  }
}
