// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NewFolderAction extends FileChooserAction implements LightEditCompatible {
  public NewFolderAction() { }

  public NewFolderAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    VirtualFile parent = fileSystemTree.getNewFileParent();
    e.getPresentation().setEnabled(parent != null && parent.isDirectory());
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    createNewFolder(fileSystemTree);
  }

  private static void createNewFolder(FileSystemTree fileSystemTree) {
    VirtualFile parent = fileSystemTree.getNewFileParent();
    if (parent == null || !parent.isDirectory()) return;

    InputValidatorEx validator = new NewFolderValidator(parent);

    String newFolderName = Messages.showInputDialog(UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"), UIBundle.message("new.folder.dialog.title"), Messages.getQuestionIcon(), "", validator);
    if (newFolderName == null) {
      return;
    }
    Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFolder(parent, newFolderName);
    if (failReason != null) {
      Messages.showMessageDialog(UIBundle.message("create.new.folder.could.not.create.folder.error.message", newFolderName), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
    }
  }

  private static class NewFolderValidator implements InputValidatorEx {
    private final VirtualFile myDirectory;
    private @NlsContexts.DetailedDescription String myErrorText;

    NewFolderValidator(VirtualFile directory) {
      myDirectory = directory;
    }

    @Override
    public @Nullable String getErrorText(String inputString) {
      return myErrorText;
    }

    @Override
    public boolean checkInput(String inputString) {
      boolean firstToken = true;
      for (String token : StringUtil.tokenize(inputString, "\\/")) {
        if (firstToken) {
          final VirtualFile child = myDirectory.findChild(token);
          if (child != null) {
            if (child.isDirectory()) {
              myErrorText = IdeBundle.message("dialog.message.folder.with.name.already.exists", token);
            }
            else {
              myErrorText = IdeBundle.message("dialog.message.file.with.name.already.exists", token);
            }
            return false;
          }
        }
        firstToken = false;
        if (token.equals(".") || token.equals("..")) {
          myErrorText = IdeBundle.message("directory.message.cant.create.folder", token);
          return false;
        }
        if (FileTypeManager.getInstance().isFileIgnored(token)) {
          myErrorText = IdeBundle.message("dialog.message.trying.to.create.folder.with.ignored.name");
          return true;
        }
      }
      myErrorText = null;
      return !inputString.isEmpty();
    }
  }
}
