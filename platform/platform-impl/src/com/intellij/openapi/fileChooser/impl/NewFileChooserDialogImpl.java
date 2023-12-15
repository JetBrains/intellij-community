// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.UriUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.NotNullLazyValue.lazy;
import static java.util.Objects.requireNonNullElseGet;

final class NewFileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, PathChooserDialog {
  @SuppressWarnings("SpellCheckingInspection") private static final String ZIP_FS_TYPE = "zipfs";

  private final FileChooserDescriptor myDescriptor;
  private Project myProject;
  private FileChooserPanelImpl myPanel;
  private final NotNullLazyValue<VirtualFileSystem> myLocalFs =
    lazy(() -> ApplicationManager.getApplication() != null ? StandardFileSystems.local() : new CoreLocalFileSystem());
  private final NotNullLazyValue<VirtualFileSystem> myJarFs =
    lazy(() -> ApplicationManager.getApplication() != null ? StandardFileSystems.jar() : new CoreJarFileSystem());
  private @Nullable List<VirtualFile> myResults;

  NewFileChooserDialogImpl(FileChooserDescriptor descriptor, @Nullable Component parent, @Nullable Project project) {
    super(project, parent, true, IdeModalityType.IDE);
    myDescriptor = descriptor;
    myProject = project;
    setTitle(requireNonNullElseGet(descriptor.getTitle(), () -> UIBundle.message("file.chooser.default.title")));
    init();
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
    return choose(project, toSelect);
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable Project project, VirtualFile @NotNull ... toSelect) {
    if (myProject == null) myProject = project;
    myPanel.load(FileChooserUtil.getInitialPath(myDescriptor, myProject, toSelect.length > 0 ? toSelect[0] : null));
    show();
    return myResults != null ? VfsUtilCore.toVirtualFileArray(myResults) : VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<? super List<VirtualFile>> callback) {
    myPanel.load(FileChooserUtil.getInitialPath(myDescriptor, myProject, toSelect));
    show();
    if (myResults != null) {
      callback.consume(myResults);
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

    var results = new ArrayList<VirtualFile>();
    var misses = new ArrayList<@NlsSafe String>();
    for (Path path : paths) {
      var file = toVirtualFile(path);
      var adjusted = file != null && file.isValid() ? myDescriptor.getFileToSelect(file) : null;
      if (adjusted != null) {
        results.add(adjusted);
      }
      else {
        misses.add(path.toUri().toString());
      }
    }

    if (!misses.isEmpty()) {
      var urls = misses.stream().map(s -> "&nbsp;&nbsp;&nbsp;" + s).collect(Collectors.joining("<br>"));
      var message = UIBundle.message("file.chooser.vfs.lookup", urls);
      Messages.showErrorDialog(myPanel, message, getTitle());
      return;
    }

    try {
      myDescriptor.validateSelectedFiles(VfsUtilCore.toVirtualFileArray(results));
    }
    catch (Exception e) {
      Messages.showErrorDialog(myPanel, e.getMessage(), getTitle());
      return;
    }

    myResults = results;
    FileChooserUtil.updateRecentPaths(myProject, results.get(0));

    super.doOKAction();
  }

  private @Nullable VirtualFile toVirtualFile(Path path) {
    if (path.getFileSystem() == FileSystems.getDefault()) {
      return myLocalFs.get().refreshAndFindFileByPath(path.toString());
    }

    try {
      var store = path.getFileSystem().getFileStores().iterator().next();
      if (ZIP_FS_TYPE.equals(store.type())) {
        var localPath = UriUtil.trimTrailingSlashes(store.name());
        var localFile = toVirtualFile(Path.of(localPath));
        if (localFile != null) {
          return myJarFs.get().refreshAndFindFileByPath(localFile.getPath() + '!' + path);
        }
      }
    }
    catch (Exception e) {
      Logger.getInstance(NewFileChooserDialogImpl.class).warn(e);
    }

    return null;
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

  private final class ChooserDialogPanel extends JPanel implements DataProvider {
    private ChooserDialogPanel() {
      super(new BorderLayout());
      setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, new ChooserDropTarget()));
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      return FileChooserPanel.DATA_KEY.is(dataId) ? myPanel : myDescriptor.getUserData(dataId);
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
