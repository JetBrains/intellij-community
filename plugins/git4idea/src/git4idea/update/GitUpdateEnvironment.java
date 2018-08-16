// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static git4idea.GitUtil.*;

public class GitUpdateEnvironment implements UpdateEnvironment {
  private final Project myProject;
  private final GitVcsSettings mySettings;

  public GitUpdateEnvironment(@NotNull Project project, @NotNull GitVcsSettings settings) {
    myProject = project;
    mySettings = settings;
  }

  @Override
  public void fillGroups(UpdatedFiles updatedFiles) {
    //unused, there are no custom categories yet
  }

  @Override
  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] filePaths, UpdatedFiles updatedFiles, ProgressIndicator progressIndicator, @NotNull Ref<SequentialUpdatesContext> sequentialUpdatesContextRef) throws ProcessCanceledException {
    Set<VirtualFile> roots = gitRoots(Arrays.asList(filePaths));
    GitRepositoryManager repositoryManager = getRepositoryManager(myProject);
    final GitUpdateProcess gitUpdateProcess = new GitUpdateProcess(myProject,
                                                                   progressIndicator, getRepositoriesFromRoots(repositoryManager, roots),
                                                                   updatedFiles, true, true);
    boolean result = gitUpdateProcess.update(mySettings.getUpdateType()).isSuccess();
    return new GitUpdateSession(result, gitUpdateProcess.getSkippedRoots());
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
    return new GitUpdateConfigurable(mySettings);
  }

}
