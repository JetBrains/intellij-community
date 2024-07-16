// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.OwnerOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

final class NativeFileSaverDialogImpl implements FileSaverDialog {
  private final FileSaverDescriptor myDescriptor;
  private final FileChooserDialogHelper myHelper;
  private final FileDialog myFileDialog;

  NativeFileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, Project project) {
    this(descriptor, IdeFocusManager.getInstance(project).getFocusOwner());
  }

  NativeFileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, Component parent) {
    if (SystemInfo.isWindows) {
      System.setProperty("sun.awt.windows.useCommonItemDialog", "true");
    }

    myDescriptor = descriptor;
    myHelper = new FileChooserDialogHelper(descriptor);
    myHelper.setNativeDialogProperties();

    var title = Objects.requireNonNullElseGet(descriptor.getTitle(), () -> UIBundle.message("file.chooser.default.title"));
    myFileDialog = OwnerOptional.create(
      parent,
      dialog -> new FileDialog(dialog, title, FileDialog.SAVE),
      frame -> new FileDialog(frame, title, FileDialog.SAVE));
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
      var extensions = myDescriptor.getFileExtensions();
      if (extensions.length > 0) {
        filename += '.' + extensions[0];
      }
    }
    myFileDialog.setFile(filename);

    myHelper.showNativeDialog(myFileDialog);

    var file = myFileDialog.getFile();
    return file == null ? null : new VirtualFileWrapper(new File(myFileDialog.getDirectory(), file));
  }
}
