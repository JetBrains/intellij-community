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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScheduleForAdditionAction extends AnAction implements DumbAware {
  public ScheduleForAdditionAction() {
    super("Add to VCS", "Add to VCS", IconLoader.getIcon("/vcs/addToVcs.png"));
  }

  public void update(AnActionEvent e) {
    final boolean enabled = e.getData(PlatformDataKeys.PROJECT) != null && (thereAreUnversionedFiles(e));
    e.getPresentation().setEnabled(enabled);
    final String place = e.getPlace();
    if (ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION.equals(place) || ActionPlaces.CHANGES_VIEW_POPUP.equals(place) ) {
      e.getPresentation().setVisible(enabled);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final List<VirtualFile> unversionedFiles = getUnversionedFiles(e);
    if (unversionedFiles == null) {
      return;
    }
    final ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(e.getData(PlatformDataKeys.PROJECT));
    changeListManager.addUnversionedFiles(changeListManager.getDefaultChangeList(), unversionedFiles);
  }

  protected boolean thereAreUnversionedFiles(final AnActionEvent e) {
    final List<VirtualFile> unversionedFiles = getUnversionedFiles(e);
    return unversionedFiles != null && !(unversionedFiles.isEmpty());
  }

  @Nullable
  protected List<VirtualFile> getUnversionedFiles(final AnActionEvent e) {
    // first get from the ChangeListView
    List<VirtualFile> unversionedFiles = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    if (unversionedFiles != null && !unversionedFiles.isEmpty()) {
      return unversionedFiles;
    }

    // then get from selection    
    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (files == null) {
      return null;
    }
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return null;
    }
    unversionedFiles = new ArrayList<VirtualFile>(files.length);
    for (VirtualFile file : files) {
      if (file != null && FileStatusManager.getInstance(project).getStatus(file) == FileStatus.UNKNOWN) {
        unversionedFiles.add(file);
      }
    }
    return unversionedFiles.isEmpty() ? null : unversionedFiles;
  }

}
