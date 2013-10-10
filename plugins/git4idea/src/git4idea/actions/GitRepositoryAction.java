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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.TransactionRunnable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for actions that affect the entire git repository.
 * The action is available if there is at least one git root.
 */
public abstract class GitRepositoryAction extends DumbAwareAction {
  /**
   * The task delayed until end of the primary action. These tasks happen after repository refresh.
   */
  final List<TransactionRunnable> myDelayedTasks = new ArrayList<TransactionRunnable>();

  /**
   * {@inheritDoc}
   */
  public void actionPerformed(final AnActionEvent e) {
    myDelayedTasks.clear();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    final List<VirtualFile> roots = getGitRoots(project, vcs);
    if (roots == null) return;
    // get default root
    final VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    VirtualFile defaultRootVar = null;
    if (vFiles != null) {
      for (VirtualFile file : vFiles) {
        final VirtualFile root = GitUtil.gitRootOrNull(file);
        if (root != null) {
          defaultRootVar = root;
          break;
        }
      }
    }
    if (defaultRootVar == null) {
      defaultRootVar = roots.get(0);
    }
    final VirtualFile defaultRoot = defaultRootVar;
    final Set<VirtualFile> affectedRoots = new HashSet<VirtualFile>();
    String actionName = getActionName();

    List<VcsException> exceptions = new ArrayList<VcsException>();
    try {
      perform(project, roots, defaultRoot, affectedRoots, exceptions);
    }
    catch (VcsException ex) {
      exceptions.add(ex);
    }
    if (executeFinalTasksSynchronously()) {
      runFinalTasks(project, vcs, affectedRoots, actionName, exceptions);
    }
  }

  protected final void runFinalTasks(Project project, GitVcs vcs, Set<VirtualFile> affectedRoots, String actionName,
                                     List<VcsException> exceptions) {
    VcsFileUtil.refreshFiles(project, affectedRoots);
    for (TransactionRunnable task : myDelayedTasks) {
      task.run(exceptions);
    }
    myDelayedTasks.clear();
    vcs.showErrors(exceptions, actionName);
  }

  /**
   * Return true to indicate that the final tasks should be executed after the action invocation,
   * false if the task is responsible to call the final tasks manually via {@link #runFinalTasks(Project, GitVcs, Set, String, List)}.
   */
  protected boolean executeFinalTasksSynchronously() {
    return true;
  }

  protected static boolean isRebasing(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (files != null) {
        for (VirtualFile file : files) {
          GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
          if (manager == null) {
            return false;
          }
          final GitRepository repositoryForFile = manager.getRepositoryForFile(file);
          if (repositoryForFile != null && repositoryForFile.getState() == GitRepository.State.REBASING) {
            return true;
          }
        }
      }
    }
    return false;
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
   * Delay task to be executed after refresh
   *
   * @param task the task to run
   */
  public final void delayTask(@NotNull TransactionRunnable task) {
    myDelayedTasks.add(task);
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
   * @param affectedRoots a set of roots affected by the action
   * @param exceptions    a list of exceptions from running git
   * @throws VcsException if there is a problem with running git (this exception is considered to be added to the end of the exception list)
   */
  protected abstract void perform(@NotNull Project project,
                                  @NotNull List<VirtualFile> gitRoots,
                                  @NotNull VirtualFile defaultRoot,
                                  final Set<VirtualFile> affectedRoots,
                                  List<VcsException> exceptions) throws VcsException;

  /**
   * {@inheritDoc}
   */
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
