// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import git4idea.actions.GitToggleAnnotationOptionsActionProvider;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public final class GitAnnotationsListener {
  public static void registerListener(@NotNull Project project, @NotNull Disposable disposable) {
    project.getMessageBus().connect(disposable).subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
      @Override
      public void repositoryChanged(@NotNull GitRepository repository) {
        final VcsAnnotationRefresher refresher = BackgroundTaskUtil.syncPublisher(project, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED);
        refresher.dirtyUnder(repository.getRoot());
      }
    });

    VcsLogApplicationSettings logSettings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);
    VcsLogUiProperties.PropertiesChangeListener logSettingsListener = new VcsLogUiProperties.PropertiesChangeListener() {
      @Override
      public <T> void onPropertyChanged(VcsLogUiProperties.@NotNull VcsLogUiProperty<T> property) {
        if (property.equals(CommonUiProperties.PREFER_COMMIT_DATE)) {
          GitToggleAnnotationOptionsActionProvider.resetAllAnnotations(project, false);
        }
      }
    };
    logSettings.addChangeListener(logSettingsListener);
    Disposer.register(disposable, () -> logSettings.removeChangeListener(logSettingsListener));
  }
}
