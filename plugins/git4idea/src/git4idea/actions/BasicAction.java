/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitVcs;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.vfs.VirtualFileVisitor.ONE_LEVEL_DEEP;
import static com.intellij.openapi.vfs.VirtualFileVisitor.SKIP_ROOT;

/**
 * Basic abstract action handler for all Git actions to extend.
 */
public abstract class BasicAction extends DumbAwareAction {
  /**
   * {@inheritDoc}
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
    final VirtualFile[] vFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    assert vFiles != null : "The action is only available when files are selected";

    assert project != null;
    final GitVcs vcs = GitVcs.getInstance(project);
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, vFiles)) {
      return;
    }
    final String actionName = getActionName();

    final VirtualFile[] affectedFiles = collectAffectedFiles(project, vFiles);
    final List<VcsException> exceptions = new ArrayList<>();
    final boolean background = perform(project, vcs, exceptions, affectedFiles);
    if (!background) {
      GitVcs.runInBackground(new Task.Backgroundable(project, getActionName()) {
        public void run(@NotNull ProgressIndicator indicator) {
          VfsUtil.markDirtyAndRefresh(false, true, false, affectedFiles);
          VcsFileUtil.markFilesDirty(project, Arrays.asList(affectedFiles));
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
              GitUIUtil.showOperationErrors(project, exceptions, actionName);
            }
          });
        }
      });
    }
  }


  /**
   * Perform the action over set of files
   *
   * @param project       the context project
   * @param mksVcs        the vcs instance
   * @param exceptions    the list of exceptions to be collected.
   * @param affectedFiles the files to be affected by the operation
   * @return true if the operation scheduled a background job, or cleanup is not needed
   */
  protected abstract boolean perform(@NotNull Project project,
                                     GitVcs mksVcs,
                                     @NotNull List<VcsException> exceptions,
                                     @NotNull VirtualFile[] affectedFiles);

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
    List<VirtualFile> affectedFiles = new ArrayList<>(files.length);
    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    for (VirtualFile file : files) {
      if (!file.isDirectory() && projectLevelVcsManager.getVcsFor(file) instanceof GitVcs) {
        affectedFiles.add(file);
      }
      else if (file.isDirectory() && isRecursive()) {
        addChildren(project, affectedFiles, file);
      }

    }
    return VfsUtilCore.toVirtualFileArray(affectedFiles);
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
  private void addChildren(@NotNull final Project project, @NotNull final List<VirtualFile> files, @NotNull VirtualFile file) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor(SKIP_ROOT, (isRecursive() ? null : ONE_LEVEL_DEEP)) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && appliesTo(project, file)) {
          files.add(file);
        }
        return true;
      }
    });
  }

  /**
   * @return the name of action (it is used in a number of ui elements)
   */
  @NotNull
  protected abstract String getActionName();


  /**
   * @return true if the action could be applied recursively
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  protected boolean isRecursive() {
    return true;
  }

  /**
   * Check if the action is applicable to the file. The default checks if the file is a directory
   *
   * @param project the context project
   * @param file    the file to check
   * @return true if the action is applicable to the virtual file
   */
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
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (vFiles == null || vFiles.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    boolean enabled = ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, vFiles) && isEnabled(project, vcs, vFiles);
    // only enable action if all the targets are under the vcs and the action supports all of them

    presentation.setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setVisible(true);
    }
  }

  /**
   * Check if the action should be enabled for the set of the files
   *
   * @param project the context project
   * @param vcs     the vcs to use
   * @param vFiles  the set of files
   * @return true if the action should be enabled
   */
  protected abstract boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles);

  /**
   * Save all files in the application (the operation creates write action)
   */
  public static void saveAll() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}
