// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.IdeBundle;
import com.intellij.jdkEx.JdkEx;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PathChooserDialogHelper;
import com.intellij.ui.UIBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.OwnerOptional;
import com.jetbrains.JBRFileDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;


public class WinPathChooserDialog implements PathChooserDialog, FileChooserDialog {

  private FileDialog myFileDialog;
  private final FileChooserDescriptor myFileChooserDescriptor;
  private final WeakReference<Component> myParent;
  private final Project myProject;
  private final @NlsContexts.DialogTitle String myTitle;
  private VirtualFile [] virtualFiles;
  private final PathChooserDialogHelper myHelper;

  public WinPathChooserDialog(FileChooserDescriptor descriptor, Component parent, Project project) {
    myHelper = new PathChooserDialogHelper(descriptor);
    myFileChooserDescriptor = descriptor;
    myParent = new WeakReference<>(parent);
    myProject = project;
    myTitle = getChooserTitle(descriptor);

    Consumer<Dialog> dialogConsumer = owner -> myFileDialog = new FileDialog(owner, myTitle, FileDialog.LOAD);
    Consumer<Frame> frameConsumer = owner -> myFileDialog = new FileDialog(owner, myTitle, FileDialog.LOAD);

    OwnerOptional
      .fromComponent(parent)
      .ifDialog(dialogConsumer)
      .ifFrame(frameConsumer)
      .ifNull(frameConsumer);
    initExtendedProperties();
  }

  private void initExtendedProperties() {
    if (myFileDialog == null) return;

    JBRFileDialog jbrDialog = JBRFileDialog.get(myFileDialog);
    if (jbrDialog != null) {
      jbrDialog.setLocalizationStrings(IdeBundle.message("windows.native.common.dialog.open"),
                                       IdeBundle.message("windows.native.common.dialog.select.folder"));
      int hints = jbrDialog.getHints();
      if (myFileChooserDescriptor.isChooseFolders()) hints |= JBRFileDialog.SELECT_DIRECTORIES_HINT;
      if (myFileChooserDescriptor.isChooseFiles() ||
          (myFileChooserDescriptor.isChooseJars() && myFileChooserDescriptor.isChooseJarsAsFiles())) {
        hints |= JBRFileDialog.SELECT_FILES_HINT;
      }
      jbrDialog.setHints(hints);
    }
  }

  private static @NlsContexts.DialogTitle String getChooserTitle(final FileChooserDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.default.title");
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<? super List<VirtualFile>> callback) {
    if (toSelect != null && toSelect.getParent() != null) {

      String directoryName;
      String fileName = null;
      if (toSelect.isDirectory()) {
        directoryName = toSelect.getCanonicalPath();
      } else {
        directoryName = toSelect.getParent().getCanonicalPath();
        fileName = toSelect.getPath();
      }
      myFileDialog.setDirectory(directoryName);
      myFileDialog.setFile(fileName);
    }


    myFileDialog.setFilenameFilter(FileChooser.safeInvokeFilter((dir, name) -> {
      File file = new File(dir, name);
      return myFileChooserDescriptor.isFileSelectable(myHelper.fileToVirtualFile(file));
    }, false));

    myFileDialog.setMultipleMode(myFileChooserDescriptor.isChooseMultiple());

    final CommandProcessorEx commandProcessor =
      ApplicationManager.getApplication() != null ? (CommandProcessorEx)CommandProcessor.getInstance() : null;
    final boolean appStarted = commandProcessor != null;


    if (appStarted) {
      commandProcessor.enterModal();
      LaterInvocator.enterModal(myFileDialog);
    }

    Component parent = myParent.get();
    try {
      myFileDialog.setVisible(true);
    }
    finally {
      if (appStarted) {
        commandProcessor.leaveModal();
        LaterInvocator.leaveModal(myFileDialog);
        if (parent != null) parent.requestFocus();
      }
    }

    File[] files = myFileDialog.getFiles();
    List<VirtualFile> virtualFileList = myHelper.getChosenFiles(files);
    virtualFiles = virtualFileList.toArray(VirtualFile.EMPTY_ARRAY);

    if (!virtualFileList.isEmpty()) {
      try {
        if (virtualFileList.size() == 1) {
          myFileChooserDescriptor.isFileSelectable(virtualFileList.get(0));
        }
        myFileChooserDescriptor.validateSelectedFiles(virtualFiles);
      }
      catch (Exception e) {
        if (parent == null) {
          Messages.showErrorDialog(myProject, e.getMessage(), myTitle);
        }
        else {
          Messages.showErrorDialog(parent, e.getMessage(), myTitle);
        }

        return;
      }

      if (!ArrayUtil.isEmpty(files)) {
        callback.consume(virtualFileList);
      }
      else if (callback instanceof FileChooser.FileChooserConsumer) {
        ((FileChooser.FileChooserConsumer)callback).cancelled();
      }
    }
  }

  @NotNull
  private static FileDialog createFileDialogWithoutOwner(String title, int load) {
    // This is bad. But sometimes we do not have any windows at all.
    // On the other hand, it is a bit strange to show a file dialog without an owner
    // Therefore we should minimize usage of this case.
    return new FileDialog((Frame)null, title, load);
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
    choose(toSelect, files -> {});
    return virtualFiles;
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable Project project, VirtualFile @NotNull ... toSelect) {
    return choose((toSelect.length > 0 ? toSelect[0] : null), project);
  }
}
