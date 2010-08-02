/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.TransactionRunnable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base class for actions that affect the entire git repository.
 * The action is available if there is at least one git root.
 */
public abstract class GitRepositoryAction extends DumbAwareAction {
  /**
   * The task delayed until end of the primary action. These tasks happen after repository refresh.
   */
  final LinkedList<TransactionRunnable> myDelayedTasks = new LinkedList<TransactionRunnable>();

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
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    final List<VirtualFile> roots = getGitRoots(project, vcs);
    if (roots == null) return;
    // get default root
    final VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
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
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
    //Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction and returns all exceptions together.
    List<VcsException> exceptions = helper.runTransactionRunnable(vcs, new TransactionRunnable() {
      public void run(List<VcsException> exceptions) {
        //noinspection unchecked
        try {
          perform(project, roots, defaultRoot, affectedRoots, exceptions);
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
        GitUtil.refreshFiles(project, affectedRoots);
        for (TransactionRunnable task : myDelayedTasks) {
          task.run(exceptions);
        }
      }
    }, null);
    myDelayedTasks.clear();
    vcs.showErrors(exceptions, actionName);
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
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
    if (roots == null || roots.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    presentation.setEnabled(true);
    presentation.setVisible(true);
  }
}
