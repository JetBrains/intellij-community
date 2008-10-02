package git4idea.actions;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBundle;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic abstract action handler for all Git actions to extend.
 */
public abstract class BasicAction extends AnAction {
  //	protected MksConfiguration configuration;
  protected static final String ACTION_CANCELLED_MSG = GitBundle.message("command.cancelled");

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
    final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);

    final GitVcs vcs = GitVcs.getInstance(project);
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, vFiles)) {
      return;
    }

    String actionName = getActionName(vcs);
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);

    //Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction and returns all exceptions together.
    List<VcsException> exceptions = helper.runTransactionRunnable(vcs, new TransactionRunnable() {
      public void run(List<VcsException> exceptions) {
        final VirtualFile[] affectedFiles = collectAffectedFiles(project, vFiles);
        //noinspection unchecked
        try {
          perform(project, vcs, exceptions, affectedFiles);
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
        refreshFiles(project, affectedFiles);
      }

    }, null);
    vcs.showErrors(exceptions, actionName);
  }


  private void refreshFiles(@NotNull final Project project, @NotNull final VirtualFile[] affectedFiles) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (VirtualFile file : affectedFiles) {
          file.refresh(false, true);
          FileStatusManager.getInstance(project).fileStatusChanged(file);
        }
      }

    });

  }

  protected abstract void perform(@NotNull Project project,
                                  GitVcs mksVcs,
                                  @NotNull List<VcsException> exceptions,
                                  @NotNull VirtualFile[] affectedFiles) throws VcsException;

  /**
   * given a list of action-target files, returns ALL the files that should be
   * subject to the action Does not keep directories, but recursively adds
   * directory contents
   *
   * @param project the project subject of the action
   * @param files   the root selection
   * @return the complete set of files this action should apply to
   */
  @NotNull
  protected VirtualFile[] collectAffectedFiles(@NotNull Project project, @NotNull VirtualFile[] files) {
    List<VirtualFile> affectedFiles = new ArrayList<VirtualFile>(files.length);
    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    for (VirtualFile file : files) {
      if (!file.isDirectory() && projectLevelVcsManager.getVcsFor(file) instanceof GitVcs) {
        affectedFiles.add(file);
      }
      else if (file.isDirectory() && isRecursive()) {
        addChildren(project, affectedFiles, file);
      }

    }
    return affectedFiles.toArray(new VirtualFile[affectedFiles.size()]);
  }

  /**
   * recursively adds all the children of file to the files list, for which
   * this action makes sense ({@link #appliesTo(com.intellij.openapi.project.Project,com.intellij.openapi.vfs.VirtualFile)}
   * returns true)
   *
   * @param project the project subject of the action
   * @param files   result list
   * @param file    the file whose children should be added to the result list
   *                (recursively)
   */
  private void addChildren(@NotNull Project project, @NotNull List<VirtualFile> files, @NotNull VirtualFile file) {
    VirtualFile[] children = file.getChildren();
    for (VirtualFile child : children) {
      if (!child.isDirectory() && appliesTo(project, child)) {
        files.add(child);
      }
      else if (child.isDirectory() && isRecursive()) {
        addChildren(project, files, child);
      }
    }
  }

  @NotNull
  protected abstract String getActionName(@NotNull AbstractVcs abstractvcs);

  protected boolean isRecursive() {
    return true;
  }

  protected boolean appliesTo(@NotNull Project project, @NotNull VirtualFile file) {
    return !file.isDirectory();
  }

  /**
   * Disable the action if the event does not apply in this context.
   *
   * @param e The update event
   */
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(PlatformDataKeys.PROJECT.getName());
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] vFiles = (VirtualFile[])dataContext.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName());
    if (vFiles == null || vFiles.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }
    GitVcs mksvcs = GitVcs.getInstance(project);
    boolean enabled =
        ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(mksvcs, vFiles) && isEnabled(project, mksvcs, vFiles);
    // only enable action if all the targets are under the vcs and the action suports all of them

    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  protected abstract boolean isEnabled(@NotNull Project project, @NotNull GitVcs mksvcs, @NotNull VirtualFile... vFiles);

  public static void saveAll() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}
