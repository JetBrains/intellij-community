/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:13:55
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScheduleForAdditionAction extends AnAction implements DumbAware {

  public void update(@NotNull AnActionEvent e) {
    final boolean enabled = thereAreUnversionedFiles(e);
    e.getPresentation().setEnabled(enabled);
    final String place = e.getPlace();
    if (ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION.equals(place) || ActionPlaces.CHANGES_VIEW_POPUP.equals(place) ) {
      e.getPresentation().setVisible(enabled);
    }
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    final List<VirtualFile> unversionedFiles = getUnversionedFiles(e);
    if (unversionedFiles.isEmpty()) {
      return;
    }
    FileDocumentManager.getInstance().saveAllDocuments();
    final ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(e.getData(CommonDataKeys.PROJECT));
    changeListManager.addUnversionedFiles(changeListManager.getDefaultChangeList(), unversionedFiles, new Condition<FileStatus>() {
      @Override
      public boolean value(FileStatus status) {
        return isStatusForAddition(status);
      }
    });
  }

  private boolean thereAreUnversionedFiles(AnActionEvent e) {
    List<VirtualFile> unversionedFiles = getFromChangesView(e);
    if (unversionedFiles != null && !unversionedFiles.isEmpty()) {
      return true;
    }
    VirtualFile[] files = getFromSelection(e);
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (files == null || project == null) {
      return false;
    }
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    for (VirtualFile file : files) {
      if (isFileUnversioned(file, vcsManager, fileStatusManager)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private List<VirtualFile> getUnversionedFiles(final AnActionEvent e) {
    List<VirtualFile> unversionedFiles = getFromChangesView(e);
    if (unversionedFiles != null && !unversionedFiles.isEmpty()) {
      return unversionedFiles;
    }

    final VirtualFile[] files = getFromSelection(e);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (files == null || project == null) {
      return Collections.emptyList();
    }
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    unversionedFiles = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      if (isFileUnversioned(file, vcsManager, fileStatusManager)) {
        unversionedFiles.add(file);
      }
    }
    return unversionedFiles;
  }

  private boolean isFileUnversioned(@NotNull VirtualFile file,
                                    @NotNull ProjectLevelVcsManager vcsManager, @NotNull FileStatusManager fileStatusManager) {
    AbstractVcs vcs = vcsManager.getVcsFor(file);
    return vcs != null && !vcs.areDirectoriesVersionedItems() && file.isDirectory() ||
           isStatusForAddition(fileStatusManager.getStatus(file));
  }

  protected boolean isStatusForAddition(FileStatus status) {
    return status == FileStatus.UNKNOWN;
  }

  @Nullable
  private static List<VirtualFile> getFromChangesView(AnActionEvent e) {
    return e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
  }

  @Nullable
  private static VirtualFile[] getFromSelection(AnActionEvent e) {
    return CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
  }

}
