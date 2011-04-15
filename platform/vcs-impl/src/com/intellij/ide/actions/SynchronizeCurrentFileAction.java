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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;

public class SynchronizeCurrentFileAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    VirtualFile[] files = getFiles(e);

    if (getEventProject(e) == null || files == null || files.length == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }

    String message = getMessage(files);
    e.getPresentation().setEnabled(true);
    e.getPresentation().setText(message);
  }

  private static String getMessage(VirtualFile[] files) {
    if (files.length == 1) {
      return IdeBundle.message("action.synchronize.file", files[0].getName().replace("_", "__").replace("&", "&&"));
    }
    return IdeBundle.message("action.synchronize.selected.files");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = getFiles(e);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (VirtualFile f : files) {
          f.refresh(false, true);
        }
      }
    });

    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    for (VirtualFile f : files) {
      if (f.isDirectory()) {
        dirtyScopeManager.dirDirtyRecursively(f);
      }
      else {
        dirtyScopeManager.fileDirty(f);
      }
    }

    String message = IdeBundle.message("action.sync.completed.successfully", getMessage(files));
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
  }

  private static VirtualFile[] getFiles(AnActionEvent e) {
    return e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
  }
}