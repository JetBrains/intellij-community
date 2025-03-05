// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.OwnerOptional;
import com.jetbrains.JBRFileDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.file.Path;

import static java.util.Objects.requireNonNullElseGet;

final class NativeFileSaverDialogImpl implements FileSaverDialog {
  private final FileSaverDescriptor myDescriptor;
  private final FileChooserDialogHelper myHelper;
  private final FileDialog myFileDialog;

  NativeFileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, Project project) {
    this(descriptor, IdeFocusManager.getInstance(project).getFocusOwner());
  }

  NativeFileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, Component parent) {
    myDescriptor = descriptor;
    myHelper = new FileChooserDialogHelper(descriptor);
    myHelper.setNativeDialogProperties();

    var title = requireNonNullElseGet(descriptor.getTitle(), () -> UIBundle.message("file.chooser.default.title"));
    myFileDialog = OwnerOptional.create(
      parent,
      dialog -> new FileDialog(dialog, title, FileDialog.SAVE),
      frame -> new FileDialog(frame, title, FileDialog.SAVE));

    var jbrDialog = JBRFileDialog.get(myFileDialog);
    if (jbrDialog != null) {
      jbrDialog.setLocalizationString(JBRFileDialog.ALL_FILES_COMBO_KEY, IdeBundle.message("windows.native.common.dialog.all"));
      myHelper.setFileFilter(jbrDialog, descriptor);
    }
  }

  @Override
  public @Nullable VirtualFileWrapper save(@Nullable VirtualFile baseDir, @Nullable String filename) {
    return doSave(baseDir == null ? null : baseDir.getCanonicalPath(), filename);
  }

  @Override
  public @Nullable VirtualFileWrapper save(@Nullable Path baseDir, @Nullable String filename) {
    return doSave(baseDir == null ? null : baseDir.toAbsolutePath().normalize().toString(), filename);
  }

  private @Nullable VirtualFileWrapper doSave(@Nullable String baseDir, @Nullable String filename) {
    myFileDialog.setDirectory(baseDir);

    if (filename != null && filename.indexOf('.') < 0) {
      var extensionFilter = myDescriptor.getExtensionFilter();
      if (extensionFilter != null) {
        filename += '.' + extensionFilter.second.get(0);
      }
    }
    myFileDialog.setFile(filename);

    myHelper.showNativeDialog(myFileDialog);

    var files = myFileDialog.getFiles();
    return files.length != 0 ? new VirtualFileWrapper(files[0]) : null;
  }
}
