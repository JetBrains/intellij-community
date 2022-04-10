// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.util.Objects.requireNonNullElse;

public class NewFileAction extends FileChooserAction implements LightEditCompatible {
  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    var visible = e.getData(FileChooserKeys.NEW_FILE_TYPE) != null;
    var directory = panel.currentDirectory();
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && directory != null && !directory.getFileSystem().isReadOnly());
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    var directory = panel.currentDirectory();
    var fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
    if (fileType == null || directory == null || directory.getFileSystem().isReadOnly()) return;

    var title = UIBundle.message("file.chooser.new.file.title");
    var prompt = UIBundle.message("file.chooser.new.file.prompt");
    var initial = "newFile." + fileType.getDefaultExtension();
    var selection = new TextRange(0, 7);
    var validator = new FileNameInputValidator(directory.getFileSystem());

    while (true) {
      var input = MessagesService.getInstance().showInputDialog(null, panel.getComponent(), prompt, title, null, initial, validator, selection, null);
      if (input == null) break;
      var name = input.trim();
      initial = name;
      selection = null;

      var progress = UIBundle.message("file.chooser.creating.progress", name);
      try {
        var newFile = ProgressManager.getInstance().run(new Task.WithResult<Path, IOException>(e.getProject(), panel.getComponent(), progress, true) {
          @Override
          protected Path compute(@NotNull ProgressIndicator indicator) throws IOException {
            indicator.setIndeterminate(true);
            var newFile = directory.resolve(name);
            NioFiles.createDirectories(newFile.getParent());
            var content = requireNonNullElse(e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT), "");
            Files.writeString(newFile, content, StandardOpenOption.CREATE_NEW);
            return newFile;
          }
        });
        panel.reload(newFile);
        break;
      }
      catch (IOException | InvalidPathException ex) {
        Messages.showErrorDialog(panel.getComponent(), IoErrorText.message(ex), CommonBundle.getErrorTitle());
      }
    }
  }

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
    if (fileType == null || initialContent == null) return;

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
