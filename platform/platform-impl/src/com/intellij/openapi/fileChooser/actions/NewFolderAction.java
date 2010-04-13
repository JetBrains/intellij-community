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
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;

import javax.swing.*;

public class NewFolderAction extends FileChooserAction {
  public NewFolderAction() {
  }

  public NewFolderAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    VirtualFile parent = fileSystemTree.getNewFileParent();
    presentation.setEnabled(parent != null && parent.isDirectory());
    setEnabledInModalContext(true);
  }

  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    createNewFolder(fileSystemTree);
  }

  private static void createNewFolder(FileSystemTree fileSystemTree) {
    final VirtualFile file = fileSystemTree.getNewFileParent();
    if (file == null || !file.isDirectory()) return;

    String newFolderName;
    while (true) {
      newFolderName = Messages.showInputDialog(UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
                                               UIBundle.message("new.folder.dialog.title"), Messages.getQuestionIcon());
      if (newFolderName == null) {
        return;
      }
      if ("".equals(newFolderName.trim())) {
        Messages.showMessageDialog(UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFolder(file, newFolderName);
      if (failReason != null) {
        Messages.showMessageDialog(UIBundle.message("create.new.folder.could.not.create.folder.error.message", newFolderName),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      return;
    }
  }
}
