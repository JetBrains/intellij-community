// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
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
import org.jetbrains.annotations.NotNull;

public class NewFileAction extends FileChooserAction implements LightEditCompatible {
  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    FileType fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
    String initialContent = e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT);
    if (fileType != null && initialContent != null) {
      presentation.setVisible(true);
      VirtualFile selectedFile = fileSystemTree.getNewFileParent();
      presentation.setEnabled(selectedFile != null && selectedFile.isDirectory());
      presentation.setIcon(LayeredIcon.create(fileType.getIcon(), AllIcons.Actions.New));
    }
    else {
      presentation.setEnabledAndVisible(false);
    }
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    FileType fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
    String initialContent = e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT);
    if (fileType != null && initialContent != null) {
      createNewFile(fileSystemTree, fileType, initialContent);
    }
  }

  private static void createNewFile(FileSystemTree fileSystemTree, FileType fileType, String initialContent) {
    VirtualFile file = fileSystemTree.getNewFileParent();
    if (file == null || !file.isDirectory()) return;

    String name;
    while (true) {
      name = Messages.showInputDialog(UIBundle.message("create.new.file.enter.new.file.name.prompt.text"), UIBundle.message("new.file.dialog.title"), Messages.getQuestionIcon());
      if (name == null) {
        return;
      }
      name = name.strip();
      if (name.isEmpty()) {
        Messages.showMessageDialog(UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFile(file, name, fileType, initialContent);
      if (failReason != null) {
        Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", name), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      return;
    }
  }
}
