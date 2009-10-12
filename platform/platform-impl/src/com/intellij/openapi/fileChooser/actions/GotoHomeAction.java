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
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;

/**
 * @author Vladimir Kondratyev
 */
public final class GotoHomeAction extends FileChooserAction {
  protected void actionPerformed(final FileSystemTree fileSystemTree, AnActionEvent e) {
    final VirtualFile homeDirectory = getHomeDirectory();
    fileSystemTree.select(homeDirectory, new Runnable() {
      public void run() {
        fileSystemTree.expand(homeDirectory, null);
      }
    });
  }

  private static VirtualFile getHomeDirectory(){
    return LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if(!presentation.isEnabled()){
      return;
    }
    final VirtualFile homeDirectory = getHomeDirectory();
    presentation.setEnabled(homeDirectory != null && (fileSystemTree).isUnderRoots(homeDirectory));
  }
}
