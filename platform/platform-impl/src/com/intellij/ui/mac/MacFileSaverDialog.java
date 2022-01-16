// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.PathChooserDialogHelper;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.ui.OwnerOptional;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;

/**
 * @author Denis Fokin
 */
public final class MacFileSaverDialog implements FileSaverDialog {
  private FileDialog myFileDialog;
  private final FileSaverDescriptor myDescriptor;

  private static String getChooserTitle(final FileChooserDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.default.title");
  }

  public MacFileSaverDialog(FileSaverDescriptor descriptor, Project project) {
    this(descriptor, IdeFocusManager.getInstance(project).getFocusOwner());
  }

  public MacFileSaverDialog(FileSaverDescriptor descriptor, Component parent) {
    String title = getChooserTitle(descriptor);
    Consumer<Dialog> dialogConsumer = owner -> myFileDialog = new FileDialog(owner, title, FileDialog.SAVE);
    Consumer<Frame> frameConsumer = owner -> myFileDialog = new FileDialog(owner, title, FileDialog.SAVE);

    myDescriptor = descriptor;

    OwnerOptional
      .fromComponent(parent)
      .ifDialog(dialogConsumer)
      .ifFrame(frameConsumer)
      .ifNull(frameConsumer);
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
    myFileDialog.setFile(filename);
    myFileDialog.setFilenameFilter(FileChooser.safeInvokeFilter((dir, name) -> {
      return myDescriptor.isFileSelectable(PathChooserDialogHelper.fileToCoreLocalVirtualFile(dir, name));
    }, false));

    myFileDialog.setVisible(true);

    String file = myFileDialog.getFile();
    return file == null ? null : new VirtualFileWrapper(new File(myFileDialog.getDirectory() + File.separator + file));
  }
}
