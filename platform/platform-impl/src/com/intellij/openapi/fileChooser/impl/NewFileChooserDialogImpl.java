// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
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
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.NotNullLazyValue.lazy;
import static java.util.Objects.requireNonNullElseGet;

final class NewFileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, PathChooserDialog {
  private static final String RECENT_FILES_KEY = "file.chooser.recent.files";  // only local paths, VFS format
  private static final int RECENT_FILES_LIMIT = 30;

  private final FileChooserDescriptor myDescriptor;
  private FileChooserPanelImpl myPanel;
  private final NotNullLazyValue<VirtualFileSystem> myLocalFs =
    lazy(() -> ApplicationManager.getApplication() != null ? StandardFileSystems.local() : new CoreLocalFileSystem());
  private final NotNullLazyValue<VirtualFileSystem> myJarFs =
    lazy(() -> ApplicationManager.getApplication() != null ? StandardFileSystems.jar() : new CoreJarFileSystem());
  private @Nullable List<VirtualFile> myResults;

  NewFileChooserDialogImpl(FileChooserDescriptor descriptor, @Nullable Component parent, @Nullable Project project) {
    super(project, parent, true, IdeModalityType.PROJECT);
    myDescriptor = descriptor;
    setTitle(requireNonNullElseGet(descriptor.getTitle(), () -> UIBundle.message("file.chooser.default.title")));
    init();
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
    return choose(project, toSelect);
  }

  @Override
  public VirtualFile @NotNull [] choose(@Nullable Project project, VirtualFile @NotNull ... toSelect) {
    myPanel.load(initialPath(toSelect.length > 0 ? toSelect[0] : null));
    show();
    return myResults != null ? VfsUtilCore.toVirtualFileArray(myResults) : VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<? super List<VirtualFile>> callback) {
    myPanel.load(initialPath(toSelect));
    show();
    if (myResults != null) {
      callback.consume(myResults);
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  private static @Nullable Path initialPath(@Nullable VirtualFile provided) {
    if (provided != null) {
      var path = provided.getFileSystem().getNioPath(provided);
      if (path != null) return path;
    }

    return NioFiles.toPath(recentPaths().findFirst().orElse(SystemProperties.getUserHome()));
  }

  @Override
  protected JComponent createCenterPanel() {
    Path[] recentPaths = recentPaths().map(NioFiles::toPath).filter(Objects::nonNull).toArray(Path[]::new);
    myPanel = new FileChooserPanelImpl(myDescriptor, this::doOKAction, recentPaths);
    myPanel.setPreferredSize(JBUI.size(400));

    var dndLabel = new JLabel(IdeBundle.message("chooser.tooltip.drag.drop"), SwingConstants.CENTER);
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
      Messages.showWarningDialog(myPanel, IdeBundle.message("chooser.nothing.selected"), getTitle());
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
      var message = IdeBundle.message("chooser.vfs.lookup", urls);
      Messages.showErrorDialog(myPanel, message, getTitle());
      myPanel.reload();
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
    updateRecentPaths(results.get(0));

    super.doOKAction();
  }

  private @Nullable VirtualFile toVirtualFile(Path path) {
    var scheme = path.toUri().getScheme();

    if ("file".equals(scheme)) {
      return myLocalFs.get().refreshAndFindFileByPath(path.toString());
    }

    if ("jar".equals(scheme)) {
      try {
        var localUri = Strings.trimEnd(path.getRoot().toUri().getRawSchemeSpecificPart(), "!/");
        var localFile = toVirtualFile(Path.of(new URI(localUri)));
        if (localFile != null) {
          return myJarFs.get().refreshAndFindFileByPath(localFile.getPath() + '!' + path);
        }
      }
      catch (Exception e) {
        Logger.getInstance(NewFileChooserDialogImpl.class).warn(e);
      }
    }

    return null;
  }

  private static Stream<String> recentPaths() {
    List<String> values = PropertiesComponent.getInstance().getList(RECENT_FILES_KEY);
    return values != null ? values.stream() : Stream.empty();
  }

  private static void updateRecentPaths(VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      List<String> newValues = Stream.concat(Stream.of(file.getPath()), recentPaths())
        .filter(p -> NioFiles.toPath(p) != null)
        .distinct()
        .limit(RECENT_FILES_LIMIT)
        .collect(Collectors.toList());
      PropertiesComponent.getInstance().setList(RECENT_FILES_KEY, newValues);
    }
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

  private class ChooserDialogPanel extends JPanel implements DataProvider {
    private ChooserDialogPanel() {
      super(new BorderLayout());
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      return FileChooserPanel.DATA_KEY.is(dataId) ? myPanel : myDescriptor.getUserData(dataId);
    }
  }
}
