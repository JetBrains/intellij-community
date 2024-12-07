// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.ui.OwnerOptional;
import com.jetbrains.JBRFileDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElseGet;

final class NativeFileChooserDialogImpl implements FileChooserDialog, PathChooserDialog {
  private final FileChooserDescriptor myDescriptor;
  private final Component myParent;
  private final FileChooserDialogHelper myHelper;
  private final @NlsContexts.DialogTitle String myTitle;
  private final FileDialog myFileDialog;
  private VirtualFile[] myChosenFiles = VirtualFile.EMPTY_ARRAY;

  NativeFileChooserDialogImpl(@NotNull FileChooserDescriptor descriptor, Component parent, Project project) {
    myDescriptor = descriptor;
    myParent = parent != null ? parent : IdeFocusManager.getInstance(project).getFocusOwner();
    myHelper = new FileChooserDialogHelper(descriptor);
    myHelper.setNativeDialogProperties();

    myTitle = requireNonNullElseGet(descriptor.getTitle(), () -> UIBundle.message("file.chooser.default.title"));
    myFileDialog = OwnerOptional.create(
      parent,
      owner -> new FileDialog(owner, myTitle, FileDialog.LOAD),
      owner -> new FileDialog(owner, myTitle, FileDialog.LOAD));

    var jbrDialog = JBRFileDialog.get(myFileDialog);
    if (jbrDialog != null) {
      int hints = jbrDialog.getHints();
      if (myDescriptor.isChooseFolders()) hints |= JBRFileDialog.SELECT_DIRECTORIES_HINT;
      if (myDescriptor.isChooseFiles() || myDescriptor.isChooseJarContents()) hints |= JBRFileDialog.SELECT_FILES_HINT;
      jbrDialog.setHints(hints);
      jbrDialog.setLocalizationString(JBRFileDialog.OPEN_FILE_BUTTON_KEY, IdeBundle.message("windows.native.common.dialog.open"));
      jbrDialog.setLocalizationString(JBRFileDialog.OPEN_DIRECTORY_BUTTON_KEY, IdeBundle.message("windows.native.common.dialog.select.folder"));
      jbrDialog.setLocalizationString(JBRFileDialog.ALL_FILES_COMBO_KEY, IdeBundle.message("windows.native.common.dialog.all"));
      myHelper.setFileFilter(jbrDialog, descriptor);
    }
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @SuppressWarnings("UsagesOfObsoleteApi") @NotNull Consumer<? super List<VirtualFile>> callback) {
    if (toSelect != null && toSelect.getParent() != null) {
      String directoryName, fileName;
      if (toSelect.isDirectory()) {
        directoryName = toSelect.getCanonicalPath();
        fileName = null;
      }
      else {
        directoryName = toSelect.getParent().getCanonicalPath();
        fileName = toSelect.getPath();
      }
      myFileDialog.setDirectory(directoryName);
      myFileDialog.setFile(fileName);
    }

    myFileDialog.setMultipleMode(myDescriptor.isChooseMultiple());

    myHelper.showNativeDialog(myFileDialog);

    var selectedFiles = myFileDialog.getFiles();
    if (selectedFiles.length != 0) {
      var selectedPaths = Stream.of(selectedFiles).map(File::toPath).toList();
      myChosenFiles = myHelper.selectedFiles(selectedPaths, myParent, myTitle);
      if (myChosenFiles.length != 0) {
        FileChooserUsageCollector.log(this, myDescriptor, myChosenFiles);
        callback.consume(List.of(myChosenFiles));
      }
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable Project project, VirtualFile @NotNull ... toSelect) {
    choose(toSelect.length > 0 ? toSelect[0] : null, __ -> { });
    FileChooserUsageCollector.log(this, myDescriptor, myChosenFiles);
    return myChosenFiles;
  }
}
