// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.PostponableLogRefresher;
import git4idea.branch.GitBranchPair;
import git4idea.config.GitVcsSettings;
import git4idea.config.UpdateMethod;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static git4idea.GitUtil.isUnderGit;
import static java.util.Arrays.asList;

@Service(Service.Level.PROJECT)
public final class GitUpdateEnvironment implements UpdateEnvironment {
  private final Project myProject;

  public GitUpdateEnvironment(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void fillGroups(UpdatedFiles updatedFiles) {
    //unused, there are no custom categories yet
  }

  @Override
  @NotNull
  public UpdateSession updateDirectories(FilePath @NotNull [] filePaths,
                                         UpdatedFiles updatedFiles,
                                         ProgressIndicator progressIndicator,
                                         @NotNull Ref<SequentialUpdatesContext> sequentialUpdatesContextRef)
    throws ProcessCanceledException {
    return performUpdate(myProject, filePaths, updatedFiles, progressIndicator, GitVcsSettings.getInstance(myProject).getUpdateMethod(),
                         null);
  }

  @Override
  public boolean validateOptions(Collection<FilePath> filePaths) {
    for (FilePath p : filePaths) {
      if (!isUnderGit(p)) {
        return false;
      }
    }
    return true;
  }

  @Override
  @Nullable
  public Configurable createConfigurable(Collection<FilePath> files) {
    return new GitUpdateConfigurable(GitVcsSettings.getInstance(myProject));
  }

  @Override
  @RequiresEdt
  public boolean hasCustomNotification() {
    // If the log won't be refreshed after update, we won't be able to build a visible pack for the updated range.
    // Unless we force refresh it by hands, but if we do it, calculating update project info would take enormous amount of time & memory.
    boolean keepLogUpToDate = PostponableLogRefresher.keepUpToDate();
    return Registry.is("git.update.project.info.as.log") && keepLogUpToDate;
  }

  @ApiStatus.Internal
  public static UpdateSession performUpdate(Project project,
                                            FilePath[] filePaths,
                                            UpdatedFiles updatedFiles,
                                            ProgressIndicator progressIndicator,
                                            UpdateMethod updateMethod,
                                            Map<GitRepository, GitBranchPair> updateConfig) {
    GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
    Set<GitRepository> repositories = ContainerUtil.map2SetNotNull(asList(filePaths), manager::getRepositoryForFile);

    final GitUpdateProcess gitUpdateProcess =
      new GitUpdateProcess(project, progressIndicator, repositories, updatedFiles, updateConfig, true, true);

    boolean result = gitUpdateProcess.update(updateMethod).isSuccess();

    Map<GitRepository, HashRange> updatedRanges = gitUpdateProcess.getUpdatedRanges();
    GitUpdateInfoAsLog.NotificationData notificationData = updatedRanges != null
                                                           ? new GitUpdateInfoAsLog(project, updatedRanges).calculateDataAndCreateLogTab()
                                                           : null;

    return new GitUpdateSession(project, notificationData, result, gitUpdateProcess.getSkippedRoots());
  }
}
