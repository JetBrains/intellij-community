// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNullElseGet;

final class NewFileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, PathChooserDialog {
  private final FileChooserDescriptor myDescriptor;
  private Project myProject;
  private FileChooserPanelImpl myPanel;
  private final FileChooserDialogHelper myHelper;
  private VirtualFile[] myChosenFiles = VirtualFile.EMPTY_ARRAY;

  NewFileChooserDialogImpl(FileChooserDescriptor descriptor, @Nullable Component parent, @Nullable Project project) {
    super(project, parent, true, IdeModalityType.IDE);
    myDescriptor = descriptor;
    myProject = project;
    myHelper = new FileChooserDialogHelper(descriptor);
    setTitle(requireNonNullElseGet(descriptor.getTitle(), () -> UIBundle.message("file.chooser.default.title")));
    init();
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable Project project, VirtualFile @NotNull ... toSelect) {
    if (myProject == null) myProject = project;
    myPanel.load(FileChooserUtil.getInitialPath(myDescriptor, myProject, toSelect.length > 0 ? toSelect[0] : null));
    show();
    FileChooserUsageCollector.log(this, myDescriptor, myChosenFiles);
    return myChosenFiles;
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @SuppressWarnings("UsagesOfObsoleteApi") @NotNull Consumer<? super List<VirtualFile>> callback) {
    myPanel.load(FileChooserUtil.getInitialPath(myDescriptor, myProject, toSelect));
    show();
    FileChooserUsageCollector.log(this, myDescriptor, myChosenFiles);
    if (myChosenFiles.length != 0) {
      callback.consume(List.of(myChosenFiles));
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    Path[] recentPaths = FileChooserUtil.getRecentPaths().stream().map(NioFiles::toPath).filter(Objects::nonNull).toArray(Path[]::new);
    myPanel = new FileChooserPanelImpl(myDescriptor, this::doOKAction, this::setErrorText, recentPaths);
    myPanel.setPreferredSize(JBUI.size(600, 450));

    var dndLabel = new JLabel(UIBundle.message("file.chooser.tooltip.drag.drop"), SwingConstants.CENTER);
    dndLabel.setFont(JBUI.Fonts.miniFont());
    dndLabel.setForeground(UIUtil.getLabelDisabledForeground());

    var panel = new ChooserDialogPanel();
    panel.add(myPanel, BorderLayout.CENTER);
    panel.add(dndLabel, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected void doOKAction() {
    List<Path> paths = myPanel.chosenPaths();
    if (paths.isEmpty()) {
      Messages.showWarningDialog(myPanel, UIBundle.message("file.chooser.nothing.selected"), getTitle());
      return;
    }

    myChosenFiles = myHelper.selectedFiles(paths, myPanel, getTitle());
    if (myChosenFiles.length == 0) return;

    FileChooserUtil.updateRecentPaths(myProject, myChosenFiles[0]);

    super.doOKAction();
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myPanel);
    myPanel = null;
    super.dispose();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel != null ? myPanel.getPreferredFocusedComponent() : null;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "FileChooserDialogImpl";
  }

  @Override
  protected String getHelpId() {
    return "select.path.dialog";
  }

  private final class ChooserDialogPanel extends JPanel implements UiDataProvider {
    private ChooserDialogPanel() {
      super(new BorderLayout());
      setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, new ChooserDropTarget()));
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(FileChooserPanel.DATA_KEY, myPanel);
      DataSink.uiDataSnapshot(sink, dataId -> myDescriptor.getUserData(dataId));
    }
  }

  private final class ChooserDropTarget extends DropTargetAdapter {
    @Override
    public void dragEnter(DropTargetDragEvent e) {
      if (FileCopyPasteUtil.isFileListFlavorAvailable(e.getCurrentDataFlavors())) {
        e.acceptDrag(DnDConstants.ACTION_COPY);
      }
      else {
        e.rejectDrag();
      }
    }

    @Override
    public void dragOver(DropTargetDragEvent e) {
      dragEnter(e);
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent e) {
      dragEnter(e);
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      e.acceptDrop(DnDConstants.ACTION_COPY);
      var paths = FileCopyPasteUtil.getFiles(e.getTransferable());
      if (paths != null && !paths.isEmpty()) {
        myPanel.load(paths.get(0));
        e.dropComplete(true);
      }
      else {
        e.dropComplete(false);
      }
    }
  }
}
