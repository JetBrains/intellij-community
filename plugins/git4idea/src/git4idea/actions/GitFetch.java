// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.stash.GitShelveChangesSaver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static git4idea.GitUtil.getRepositories;
import static java.util.Arrays.asList;

public class GitFetch extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      boolean hasRemotes = getRepositories(project).stream().anyMatch(repository -> !repository.getRemotes().isEmpty());
      e.getPresentation().setEnabledAndVisible(hasRemotes);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Collection<VirtualFile> roots = asList(ProjectLevelVcsManager.getInstance(project).getAllVersionedRoots());
        try {
          new GitShelveChangesSaver(project, Git.getInstance(), indicator, myTitle).saveLocalChanges(roots);
        }
        catch (VcsException ex) {
          ex.printStackTrace();
        }
        //fetchSupport(project).fetchAllRemotes(getRepositories(project)).showNotification();
      }
    });
  }
}
