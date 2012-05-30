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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.UIBundle;

public class NewFileAction extends FileChooserAction {
  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final FileType fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
    if (fileType != null) {
      presentation.setVisible(true);
      VirtualFile selectedFile = fileSystemTree.getNewFileParent();
      presentation.setEnabled(selectedFile != null && selectedFile.isDirectory());
      presentation.setIcon(LayeredIcon.create(fileType.getIcon(), AllIcons.Actions.New));
    }
    else {
      presentation.setVisible(false);
    }
  }

  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    final FileType fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
    final String initialContent = e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT);
    if (fileType != null && initialContent != null) {
      createNewFile(fileSystemTree, fileType, initialContent);
    }
  }

  private static void createNewFile(FileSystemTree fileSystemTree, final FileType fileType, final String initialContent) {
    final VirtualFile file = fileSystemTree.getNewFileParent();
    if (file == null || !file.isDirectory()) return;

    String newFileName;
    while (true) {
      newFileName = Messages.showInputDialog(UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                                               UIBundle.message("new.file.dialog.title"), Messages.getQuestionIcon());
      if (newFileName == null) {
        return;
      }
      if ("".equals(newFileName.trim())) {
        Messages.showMessageDialog(UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFile(file, newFileName, fileType, initialContent);
      if (failReason != null) {
        Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      return;
    }
  }
}
