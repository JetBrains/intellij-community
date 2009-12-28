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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.TransactionRunnable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Basic abstract action handler for all Git actions to extend.
 */
public abstract class BasicAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
    final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    assert vFiles != null : "The action is only available when files are selected";

    assert project != null;
    final GitVcs vcs = GitVcs.getInstance(project);
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, vFiles)) {
      return;
    }

    String actionName = getActionName();
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
        GitUtil.refreshFiles(project, Arrays.asList(affectedFiles));
      }

    }, null);
    vcs.showErrors(exceptions, actionName);
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
    return VfsUtil.toVirtualFileArray(affectedFiles);
  }

  /**
   * recursively adds all the children of file to the files list, for which
   * this action makes sense ({@link #appliesTo(Project, VirtualFile)}
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
  protected abstract String getActionName();

  @SuppressWarnings({"MethodMayBeStatic"})
  protected boolean isRecursive() {
    return true;
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedDeclaration"})
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
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (vFiles == null || vFiles.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    boolean enabled = ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, vFiles) && isEnabled(project, vcs, vFiles);
    // only enable action if all the targets are under the vcs and the action supports all of them

    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  protected abstract boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles);

  public static void saveAll() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}
