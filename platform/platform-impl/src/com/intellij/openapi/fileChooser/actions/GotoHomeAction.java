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
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Vladimir Kondratyev
 */
public final class GotoHomeAction extends FileChooserAction {
  protected void actionPerformed(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
    if (userHomeDir != null) {
      fileSystemTree.select(userHomeDir, new Runnable() {
        public void run() {
          fileSystemTree.expand(userHomeDir, null);
        }
      });
    }
  }

  protected void update(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (!presentation.isEnabled()) return;

    final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
    presentation.setEnabled(userHomeDir != null && fileSystemTree.isUnderRoots(userHomeDir));
  }
}
