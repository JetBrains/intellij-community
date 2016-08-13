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
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.UIBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.OwnerOptional;
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

public class MacPathChooserDialog implements PathChooserDialog, FileChooserDialog{

  private FileDialog myFileDialog;
  private final FileChooserDescriptor myFileChooserDescriptor;
  private final Component myParent;
  private final String myTitle;
  private VirtualFile [] virtualFiles;

  public MacPathChooserDialog(FileChooserDescriptor descriptor, Component parent, Project project) {

    myFileChooserDescriptor = descriptor;
    myParent = parent;
    myTitle = getChooserTitle(descriptor);

    Consumer<Dialog> dialogConsumer = owner -> myFileDialog = new FileDialog(owner, myTitle, FileDialog.LOAD);
    Consumer<Frame> frameConsumer = owner -> myFileDialog = new FileDialog(owner, myTitle, FileDialog.LOAD);

    OwnerOptional
      .fromComponent(parent)
      .ifDialog(dialogConsumer)
      .ifFrame(frameConsumer)
      .ifNull(frameConsumer);
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
    List<VirtualFile> virtualFileList = getChosenFiles(Stream.of(files));
    virtualFiles = virtualFileList.toArray(VirtualFile.EMPTY_ARRAY);

    try {
      myFileChooserDescriptor.validateSelectedFiles(virtualFiles);
    }
    catch (Exception e) {
      Messages.showErrorDialog(myParent, e.getMessage(), myTitle);
      return;
    }

    if (!ArrayUtil.isEmpty(files)) {
      callback.consume(virtualFileList);
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  @NotNull
  private static FileDialog createFileDialogWithoutOwner(String title, int load) {
    // This is bad. But sometimes we do not have any windows at all.
    // On the other hand, it is a bit strange to show a file dialog without an owner
    // Therefore we should minimize usage of this case.
    return new FileDialog((Frame)null, title, load);
  }

  @NotNull
  @Override
  public VirtualFile[] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
    choose(toSelect, files -> {});
    return virtualFiles;
  }

  @NotNull
  @Override
  public VirtualFile[] choose(@Nullable Project project, @NotNull VirtualFile... toSelect) {
    return choose((toSelect.length > 0 ? toSelect[0] : null), project);
  }
}
