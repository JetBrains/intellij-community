// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick;

import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class GitCherryPicker extends VcsCherryPicker {
  private final @NotNull Project myProject;
  private final @NotNull GitRepositoryManager myRepositoryManager;

  public GitCherryPicker(@NotNull Project project) {
    myProject = project;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
  }

  @Override
  public boolean cherryPick(@NotNull List<? extends VcsCommitMetadata> commits) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(false);
    }
    GitCherryPickProcess cherryPickProcess = new GitCherryPickProcess(myProject, commits, indicator);
    cherryPickProcess.execute();
    return cherryPickProcess.isSuccess();
  }

  @Override
  public @NotNull VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getActionTitle() {
    return DvcsBundle.message("cherry.pick.action.text");
  }

  @Override
  public boolean canHandleForRoots(@NotNull Collection<? extends VirtualFile> roots) {
    return ContainerUtil.all(roots, r -> myRepositoryManager.getRepositoryForRootQuick(r) != null);
  }
}
