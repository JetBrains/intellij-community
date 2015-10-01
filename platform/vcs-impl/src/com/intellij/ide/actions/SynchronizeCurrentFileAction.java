/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.escapeMnemonics;
import static com.intellij.openapi.util.text.StringUtil.firstLast;

public class SynchronizeCurrentFileAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    VirtualFile[] files = getFiles(e);

    if (getEventProject(e) == null || files == null || files.length == 0) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
    e.getPresentation().setText(getMessage(files));
  }

  private static String getMessage(VirtualFile[] files) {
    return files.length == 1 ? IdeBundle.message("action.synchronize.file", escapeMnemonics(firstLast(files[0].getName(), 20)))
                             : IdeBundle.message("action.synchronize.selected.files");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = getFiles(e);
    if (project == null || files == null || files.length == 0) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (VirtualFile file : files) {
          if (file instanceof NewVirtualFile) {
            ((NewVirtualFile)file).markDirtyRecursively();
          }
        }
      }
    });

    RefreshQueue.getInstance().refresh(true, true, new Runnable() {
      @Override
      public void run() {
        postRefresh(project, files);
      }
    }, files);
  }

  private static void postRefresh(Project project, VirtualFile[] files) {
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    for (VirtualFile f : files) {
      if (f.isDirectory()) {
        dirtyScopeManager.dirDirtyRecursively(f);
      }
      else {
        dirtyScopeManager.fileDirty(f);
      }
    }

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(IdeBundle.message("action.sync.completed.successfully", getMessage(files)));
    }
  }

  @Nullable
  private static VirtualFile[] getFiles(AnActionEvent e) {
    return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
  }
}