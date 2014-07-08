/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class GotoDesktopDirAction extends FileChooserAction {
  @Override
  protected void actionPerformed(final FileSystemTree tree, AnActionEvent e) {
    final VirtualFile dir = getDesktopDirectory();
    if (dir != null) {
      tree.select(dir, new Runnable() {
        @Override
        public void run() {
          tree.expand(dir, null);
        }
      });
    }
  }

  @Override
  protected void update(FileSystemTree tree, AnActionEvent e) {
    VirtualFile dir = getDesktopDirectory();
    e.getPresentation().setEnabled(dir != null && tree.isUnderRoots(dir));
  }

  @Nullable
  private static VirtualFile getDesktopDirectory() {
    File file = new File(SystemProperties.getUserHome(), "Desktop");
    return file.isDirectory() ? LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) : null;
  }
}
