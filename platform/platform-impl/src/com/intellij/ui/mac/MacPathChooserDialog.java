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

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdePopupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
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
  private final FileChooserDescriptor myFileChooserDescriptor;
  private final Component myParent;
  private final String myTitle;

  public MacPathChooserDialog(FileChooserDescriptor descriptor, Component parent, Project project) {

    //StackingPopupDispatcher.getInstance().hidePersistentPopups();
    //myDisposeActions.add(() -> StackingPopupDispatcher.getInstance().restorePersistentPopups());

    myFileDialog = parent != null
                   ? createFileDialogWithOwner(findOwnerByComponent(parent), descriptor.getTitle(), FileDialog.LOAD)
                   : createFileDialogWithoutOwner(descriptor.getTitle(), FileDialog.LOAD);

    myFileChooserDescriptor = descriptor;
    myParent = parent;
    myTitle = getChooserTitle(descriptor);
  }

  private static String getChooserTitle(final FileChooserDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.default.title");
  }

  @NotNull
  private static List<VirtualFile> getChosenFiles(final Stream<File> streamOfFiles) {

    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final List<VirtualFile> virtualFiles = new ArrayList<>();

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

    final CommandProcessorEx commandProcessor =
      ApplicationManager.getApplication() != null ? (CommandProcessorEx)CommandProcessor.getInstance() : null;
    final boolean appStarted = commandProcessor != null;


    if (appStarted) {
      commandProcessor.enterModal();
      LaterInvocator.enterModal(myFileDialog);
    }

    try {
      myFileDialog.setVisible(true);
    }
    finally {
      if (appStarted) {
        commandProcessor.leaveModal();
        LaterInvocator.leaveModal(myFileDialog);
      }
    }

    File[] files = myFileDialog.getFiles();
    List<VirtualFile> virtualFilesList = getChosenFiles(Stream.of(files));

    try {
      myFileChooserDescriptor.validateSelectedFiles(virtualFilesList.toArray(VirtualFile.EMPTY_ARRAY));
    }
    catch (Exception e) {
      Messages.showErrorDialog(myParent, e.getMessage(), myTitle);
      return;
    }

    if (!ArrayUtil.isEmpty(files)) {
      callback.consume(virtualFilesList);
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  @NotNull
  private static Window findOwnerByComponent(@NotNull Component component) {
    return (component instanceof Window) ? (Window) component : SwingUtilities.getWindowAncestor(component);
  }

  @NotNull
  private static FileDialog createFileDialogWithOwner(@NotNull Window owner, String title, int mode) {
    FileDialog fileDialog;

    IdePopupManager manager = IdeEventQueue.getInstance().getPopupManager();

    if (manager.isPopupWindow(owner)) {

      manager.closeAllPopups();

      owner = owner.getOwner();

      while (owner != null
             && !(owner instanceof Dialog)
             && !(owner instanceof Frame))
      {
        owner = owner.getOwner();
      }

      if (owner == null) {
        fileDialog = createFileDialogWithoutOwner(title, mode);
      } else {
        fileDialog = createFileDialogWithOwner(owner, title, mode);
      }

    } else {
      if (owner instanceof Frame) {
        fileDialog = new FileDialog((Frame)owner, title, mode);
      } else if (owner instanceof Dialog) {
        fileDialog = new FileDialog((Dialog)owner, title, mode);
      } else {
        throw new RuntimeException("Owner should be a frame or a dialog");
      }
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
