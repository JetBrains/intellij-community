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
 * Date: 20.12.2006
 * Time: 17:17:50
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.IgnoreUnversionedDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Iterator;
import java.util.List;

public class IgnoreUnversionedAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    final List<VirtualFile> files = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    if (files == null) return;
    removeNullFiles(files);
    if (files.isEmpty()) return;

    IgnoreUnversionedDialog.ignoreSelectedFiles(project, files);
  }

  private static void removeNullFiles(List<VirtualFile> files) {
    for (Iterator<VirtualFile> iterator = files.iterator(); iterator.hasNext();) {
      final VirtualFile next = iterator.next();
      if (next == null) {
        iterator.remove();
      }
    }
  }

  public void update(AnActionEvent e) {
    List<VirtualFile> files = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    if (files != null) {
      removeNullFiles(files);
    }
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}