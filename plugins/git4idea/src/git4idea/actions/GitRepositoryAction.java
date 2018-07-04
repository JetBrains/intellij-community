/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for actions that affect the entire git repository.
 * The action is available if there is at least one git root.
 */
public abstract class GitRepositoryAction extends DumbAwareAction {

  public void actionPerformed(@NotNull final AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GitVcs vcs = GitVcs.getInstance(project);
    final List<VirtualFile> roots = getGitRoots(project, vcs);
    if (roots == null) return;

    final VirtualFile defaultRoot = getDefaultRoot(project, roots, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));

    perform(project, roots, defaultRoot);
  }

  @NotNull
  @CalledInAwt
  private static VirtualFile getDefaultRoot(@NotNull Project project, @NotNull List<VirtualFile> roots, @Nullable VirtualFile[] vFiles) {
    if (vFiles != null) {
      for (VirtualFile file : vFiles) {
        VirtualFile root = GitUtil.gitRootOrNull(file);
        if (root != null) {
          return root;
        }
      }
    }
    GitRepository currentRepository = GitBranchUtil.getCurrentRepository(project);
    return currentRepository != null ? currentRepository.getRoot() : roots.get(0);
  }

  protected static void showErrors(@NotNull Project project, @NotNull String actionName, @NotNull List<VcsException> exceptions) {
    GitVcs.getInstance(project).showErrors(exceptions, actionName);
  }

  /**
   * @deprecated "final tasks" are not called for all actions anymore.
   * They should be called by certain actions manually if and when needed.
   */
  @Deprecated
  protected boolean executeFinalTasksSynchronously() {
    return true;
  }

  /**
   * Get git roots for the project. The method shows dialogs in the case when roots cannot be retrieved, so it should be called
   * from the event dispatch thread.
   *
   * @param project the project
   * @param vcs     the git Vcs
   * @return the list of the roots, or null
   */
  @Nullable
  public static List<VirtualFile> getGitRoots(Project project, GitVcs vcs) {
    List<VirtualFile> roots;
    try {
      roots = GitUtil.getGitRoots(project, vcs);
    }
    catch (VcsException e) {
      Messages.showErrorDialog(project, e.getMessage(),
                               GitBundle.getString("repository.action.missing.roots.title"));
      return null;
    }
    return roots;
  }

  /**
   * Get name of action (for error reporting)
   *
   * @return the name of action
   */
  @NotNull
  protected abstract String getActionName();


  /**
   * Perform action for some repositories
   *
   * @param project       a context project
   * @param gitRoots      a git roots that affect the current project (sorted by {@link VirtualFile#getPresentableUrl()})
   * @param defaultRoot   a guessed default root (based on the currently selected file list)
   * @throws VcsException if there is a problem with running git (this exception is considered to be added to the end of the exception list)
   */
  protected abstract void perform(@NotNull Project project,
                                  @NotNull List<VirtualFile> gitRoots,
                                  @NotNull VirtualFile defaultRoot);

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setVisible(true);
    }
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
