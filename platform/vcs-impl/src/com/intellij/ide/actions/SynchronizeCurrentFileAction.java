/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.Nullable;

public class SynchronizeCurrentFileAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    VirtualFile[] files = getFiles(e);

    if (getEventProject(e) == null || files == null || files.length == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }

    String message = getMessage(files);
    e.getPresentation().setEnabled(true);
    e.getPresentation().setText(message.replace("_", "__").replace("&", "&&"));
  }

  private static String getMessage(VirtualFile[] files) {
    return files.length == 1 ? IdeBundle.message("action.synchronize.file", files[0].getName())
                             : IdeBundle.message("action.synchronize.selected.files");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = getFiles(e);
    if (project == null || files == null || files.length == 0) return;

    final AccessToken token = WriteAction.start(getClass());
    try {
      for (VirtualFile file : files) {
        final VirtualFileSystem fs = file.getFileSystem();
        if (fs instanceof LocalFileSystem && file instanceof NewVirtualFile) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
    finally {
      token.finish();
    }

    final Runnable postRefreshAction = new Runnable() {
      @Override
      public void run() {
        final VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
        for (VirtualFile f : files) {
          if (f.isDirectory()) {
            dirtyScopeManager.dirDirtyRecursively(f);
          }
          else {
            dirtyScopeManager.fileDirty(f);
          }
        }

        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
          final String message = IdeBundle.message("action.sync.completed.successfully", getMessage(files));
          statusBar.setInfo(message);
        }
      }
    };

    RefreshQueue.getInstance().refresh(true, true, postRefreshAction, files);
  }

  @Nullable
  private static VirtualFile[] getFiles(AnActionEvent e) {
    return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
  }
}