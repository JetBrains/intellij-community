// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Base class for actions that affect the entire git repository.
 * The action is available if there is at least one git root.
 */
public abstract class GitRepositoryAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GitVcs vcs = GitVcs.getInstance(project);
    final List<VirtualFile> roots = getGitRoots(project, vcs);
    if (roots == null || roots.isEmpty()) return;

    GitRepository selectedRepo = GitBranchUtil.guessRepositoryForOperation(project, e.getDataContext());
    VirtualFile defaultRoot = selectedRepo != null ? selectedRepo.getRoot() : roots.get(0);
    perform(project, roots, defaultRoot);
  }

  /**
   * Get git roots for the project. The method shows dialogs in the case when roots cannot be retrieved, so it should be called
   * from the event dispatch thread.
   *
   * @return the list of the roots, or null
   */
  @Nullable
  public static List<VirtualFile> getGitRoots(Project project, GitVcs vcs) {
    try {
      VirtualFile[] contentRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
      if (ArrayUtil.isEmpty(contentRoots)) {
        throw new VcsException(GitBundle.message("repository.action.missing.roots.unconfigured.message"));
      }

      Collection<GitRepository> repositories = GitUtil.getRepositories(project);
      if (repositories.isEmpty()) {
        throw new VcsException(GitBundle.message("repository.action.missing.roots.misconfigured"));
      }

      return DvcsUtil.sortVirtualFilesByPresentation(GitUtil.getRootsFromRepositories(repositories));
    }
    catch (VcsException e) {
      Messages.showErrorDialog(project, e.getMessage(), GitBundle.message("repository.action.missing.roots.title"));
      return null;
    }
  }

  /**
   * Get name of action (for error reporting)
   *
   * @return the name of action
   */
  @NlsActions.ActionText
  @NotNull
  protected abstract String getActionName();


  /**
   * Perform action for some repositories
   *
   * @param project     a context project
   * @param gitRoots    a git roots that affect the current project (sorted by {@link VirtualFile#getPresentableUrl()})
   * @param defaultRoot a guessed default root (based on the currently selected file list)
   */
  protected abstract void perform(@NotNull Project project,
                                  @NotNull List<VirtualFile> gitRoots,
                                  @NotNull VirtualFile defaultRoot);

  @Override
  public void update(@NotNull final AnActionEvent e) {
    boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setVisible(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
    if (roots == null || roots.length == 0) {
      return false;
    }
    return true;
  }
}
