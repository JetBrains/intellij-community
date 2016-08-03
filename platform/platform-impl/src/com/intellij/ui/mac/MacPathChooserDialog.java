/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Denis Fokin
 */

public class MacPathChooserDialog implements PathChooserDialog {

  private final FileDialog myFileDialog;

  public MacPathChooserDialog(FileChooserDescriptor descriptor, Component parent, Project project) {
    myFileDialog = parent != null
                   ? createFileDialogWithOwner(findOwnerByComponent(parent), descriptor.getTitle(), FileDialog.LOAD)
                   : createFileDialogWithoutOwner(descriptor.getTitle(), FileDialog.LOAD);
  }

  @NotNull
  private static List<VirtualFile> getChosenFiles(final Stream<File> streamOfFiles) {

    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final List<VirtualFile> virtualFiles = new ArrayList<VirtualFile>();

    streamOfFiles.forEach(file -> {
      final String vfsPath = FileUtil.toSystemIndependentName(file.getAbsolutePath());
      final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByPath(vfsPath);
      if (virtualFile != null && virtualFile.isValid()) {
        virtualFiles.add(virtualFile);
      }
    });

    return virtualFiles;
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<List<VirtualFile>> callback) {
    String path = toSelect != null ? toSelect.getCanonicalPath() : null;
    myFileDialog.setFile(path);
    myFileDialog.setVisible(true);

    File[] files = myFileDialog.getFiles();
    if (!ArrayUtil.isEmpty(files)) {
      callback.consume(getChosenFiles(Stream.of(files)));
    }
  }

  @NotNull
  private static Window findOwnerByComponent(@NotNull Component component) {
    return (component instanceof Window) ? (Window) component : SwingUtilities.getWindowAncestor(component);
  }

  @NotNull
  private static FileDialog createFileDialogWithOwner(@NotNull Window owner, String title, int mode) {
    FileDialog fileDialog;
    if (owner instanceof Frame) {
      fileDialog = new FileDialog((Frame)owner, title, mode);
    } else if (owner instanceof Dialog) {
      fileDialog = new FileDialog((Dialog)owner, title, mode);
    } else {
      throw new IllegalArgumentException("Owner should be a descendant of Dialog or Frame");
    }
    return fileDialog;
  }

  @NotNull
  private static FileDialog createFileDialogWithoutOwner(String title, int load) {
    // This is bad. But sometimes we do not have any windows at all.
    // On the other hand, it is a bit strange to show a file dialog without an owner
    // Therefore we should minimize usage of this case.
    return new FileDialog((Frame)null, title, load);
  }
}
