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
 * Date: 15.12.2006
 * Time: 17:36:42
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class EditAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    List<VirtualFile> files = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    editFilesAndShowErrors(project, files);
  }

  public static void editFilesAndShowErrors(Project project, List<VirtualFile> files) {
    final List<VcsException> exceptions = new ArrayList<>();
    editFiles(project, files, exceptions);
    if (!exceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("edit.errors"));
    }
  }

  public static void editFiles(final Project project, final List<VirtualFile> files, final List<VcsException> exceptions) {
    ChangesUtil.processVirtualFilesByVcs(project, files, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final EditFileProvider provider = vcs.getEditFileProvider();
        if (provider != null) {
          try {
            provider.editFiles(VfsUtil.toVirtualFileArray(items));
          }
          catch (VcsException e1) {
            exceptions.add(e1);
          }
          for(VirtualFile file: items) {
            VcsDirtyScopeManager.getInstance(project).fileDirty(file);
            FileStatusManager.getInstance(project).fileStatusChanged(file);
          }
        }
      }
    });
  }

  public void update(final AnActionEvent e) {
    List<VirtualFile> files = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}