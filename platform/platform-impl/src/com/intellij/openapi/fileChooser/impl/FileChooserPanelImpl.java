// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.ex.FileLookup;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PlatformNioHelper;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl.FILE_CHOOSER_SHOW_PATH_PROPERTY;
import static com.intellij.openapi.util.Pair.pair;
import static java.awt.GridBagConstraints.*;

final class FileChooserPanelImpl extends JBPanel<FileChooserPanelImpl> implements FileChooserPanel, Disposable {
  private static final Logger LOG = Logger.getInstance(FileChooserPanelImpl.class);
  private static final String SEPARATOR = "!/";
  private static final CoreLocalFileSystem FS = new CoreLocalFileSystem();

  private final FileTypeRegistry myRegistry;
  private final FileChooserDescriptor myDescriptor;
  private final Runnable myCallback;
  private final @NotNull Consumer<? super @Nullable @DialogMessage String> myErrorSink;
  private final @Nullable WatchService myWatcher;
  private final Map<Path, FileSystem> myOpenFileSystems;

  private final ComboBox<PathWrapper> myPath;
  private final SortedListModel<FsItem> myModel;
  private final JBList<FsItem> myList;
  private boolean myShowPathBar;
  private boolean myPathBarActive;
  private volatile boolean myShowHiddenFiles;

  private final Object myLock = new String("file.chooser.panel.lock");
  private int myCounter = 0;
  private Pair<Integer, Future<?>> myCurrentTask = pair(-1, CompletableFuture.completedFuture(null));
  // guarded by `myLock` via `update()` callback, which the inspection doesn't detect
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private @Nullable Path myCurrentDirectory;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private final List<FsItem> myCurrentContent = new ArrayList<>();
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private @Nullable WatchKey myWatchKey;

  FileChooserPanelImpl(@NotNull FileChooserDescriptor descriptor,
                       @NotNull Runnable callback,
                       @NotNull Consumer<? super @Nullable @DialogMessage String> errorSink,
                       Path @NotNull [] recentPaths) {
    super(new GridBagLayout());

    myRegistry = FileTypeRegistry.getInstance();
    myDescriptor = descriptor;
    myCallback = callback;
    myErrorSink = errorSink;
    myWatcher = startWatchService();
    myOpenFileSystems = new ConcurrentHashMap<>();

    myShowHiddenFiles = descriptor.isShowHiddenFiles();
    myShowPathBar = PropertiesComponent.getInstance().getBoolean(FILE_CHOOSER_SHOW_PATH_PROPERTY, true);

    var label = new JLabel(descriptor.getDescription());

    var group = (DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserToolbar");
    var toolBar = ActionManager.getInstance().createActionToolbar("FileChooserDialog", group, true);
    toolBar.setTargetComponent(this);

    myPath = new ComboBox<>(Stream.of(recentPaths).map(PathWrapper::new).toArray(PathWrapper[]::new));
    setupPathBar();

    myModel = new SortedListModel<>(FsItem.COMPARATOR);
    myList = new JBList<>(myModel);
    setupDirectoryView();

    var scrollPane = ScrollPaneFactory.createScrollPane(myList);
    var pathInsets = myPath.getInsets();
    @SuppressWarnings("UseDPIAwareInsets") var scrollInsets = new Insets(JBUI.scale(5) - pathInsets.bottom, pathInsets.left, 0, pathInsets.right);
    scrollPane.setBorder(BorderFactory.createLineBorder(NamedColorUtil.getBoundsColor()));

    add(label, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.01, CENTER, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    add(toolBar.getComponent(), new GridBagConstraints(0, 1, 1, 1, 1.0, 0.01, CENTER, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    add(myPath, new GridBagConstraints(0, 2, 1, 1, 1.0, 0.01, CENTER, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    add(scrollPane, new GridBagConstraints(0, 3, 1, 1, 1.0, 0.98, CENTER, BOTH, scrollInsets, 0, 0));
  }

  private @Nullable WatchService startWatchService() {
    try {
      var watcher = FileSystems.getDefault().newWatchService();
      execute(() -> {
        while (true) {
          try {
            var key = watcher.take();
            var events = key.pollEvents();
            key.reset();
            if (!events.isEmpty()) {
              UIUtil.invokeLaterIfNeeded(() -> {
                synchronized (myLock) {
                  if (key == myWatchKey && myCurrentDirectory != null) {
                    reload(null);
                  }
                }
              });
            }
          }
          catch (InterruptedException ignored) { }
          catch (ClosedWatchServiceException e) {
            break;
          }
        }
      });
      return watcher;
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }

  private void setupPathBar() {
    myPath.setVisible(myShowPathBar);
    myPath.setEditable(true);
    var pathEditor = (JTextField)myPath.getEditor().getEditorComponent();
    pathEditor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myPathBarActive = true;
      }
    });
    pathEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (e.getType() == DocumentEvent.EventType.INSERT && e.getOffset() == 0 && e.getLength() == e.getDocument().getLength()) {
          var text = pathEditor.getText();
          if (StringUtil.isQuotedString(text)) {
            EventQueue.invokeLater(() -> pathEditor.setText(text.substring(1, text.length() - 1)));
          }
        }
      }
    });
    var finder = new LocalFsFinder(false).withBaseDir(null);
    FileLookup.LookupFilter filter =
      f -> myDescriptor.isFileVisible(new CoreLocalVirtualFile(FS, ((LocalFsFinder.IoFile)f).getFile()), myShowHiddenFiles);
    new FileTextFieldImpl(pathEditor, finder, filter, FileChooserFactoryImpl.getMacroMap(), this) {
      @Override
      protected void setTextToFile(FileLookup.LookupFile file) {
        super.setTextToFile(file);
        var path = typedPath();
        if (path != null) {
          load(path, null, 0, false);
        }
      }
    };
    pathEditor.getActionMap().put(JTextField.notifyAction, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myPath.isPopupVisible()) {
          myPath.setPopupVisible(false);
        }
        var path = typedPath();
        if (path != null) {
          load(path, null, 0, false);
        }
        else if (((String)myPath.getEditor().getItem()).isBlank()) {
          load(null, null, 0, false);
        }
      }
    });
  }

  private void setupDirectoryView() {
    myList.setCellRenderer(new MyListCellRenderer());
    myList.setSelectionMode(myDescriptor.isChooseMultiple() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
    myList.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myPathBarActive = false;
      }
    });
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
          var idx = myList.locationToIndex(e.getPoint());
          if (idx >= 0) {
            openItemAtIndex(idx, e);
          }
        }
      }
    });
    myList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiersEx() == 0) {
          int[] selection = myList.getSelectedIndices();
          if (selection.length == 1) {
            openItemAtIndex(selection[0], e);
          }
        }
      }
    });
    new ListSpeedSearch<>(myList);
    myList.getActionMap().put(ListActions.Left.ID, myList.getActionMap().get(ListActions.Home.ID));
    myList.getActionMap().put(ListActions.Right.ID, myList.getActionMap().get(ListActions.End.ID));
    myList.getActionMap().put(FileChooserPanelActions.Root.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        load(null, myCurrentDirectory, 0, true);
      }
    });
    myList.getActionMap().put(FileChooserPanelActions.LevelUp.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myCurrentDirectory != null) {
          load(parent(myCurrentDirectory), null, UPPER_LEVEL, true);
        }
      }
    });
  }

  private @Nullable Path typedPath() {
    var object = myPath.getEditor().getItem();
    if (object instanceof PathWrapper) {
      return ((PathWrapper)object).path;
    }
    if (object instanceof String && !((String)object).isBlank()) {
      var path = findByPath(FileUtil.expandUserHome(((String)object).trim()));
      if (path != null && path.isAbsolute()) {
        return path;
      }
    }
    return null;
  }

  private void openItemAtIndex(int idx, InputEvent e) {
    FsItem item = myModel.get(idx);
    if (item.directory) {
      load(item.path, null, FsItem.UPLINK.equals(item.name) ? UPPER_LEVEL : 0, true);
    }
    else if (myDescriptor.isChooseJarContents() && myRegistry.getFileTypeByFileName(item.name) == ArchiveFileType.INSTANCE) {
      load(item.path, null, INTO_ARCHIVE, true);
    }
    else {
      myCallback.run();
    }
    e.consume();
  }

  JComponent getPreferredFocusedComponent() {
    return myShowPathBar ? myPath : myList;
  }

  @NotNull List<@NotNull Path> chosenPaths() {
    if (myShowPathBar && myPathBarActive) {
      var path = typedPath();
      return path != null ? List.of(path) : List.of();
    }
    else {
      return myList.getSelectedValuesList().stream()
        .filter(r -> r.selectable)
        .map(r -> FsItem.UPLINK.equals(r.name) ? myCurrentDirectory : r.path)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public void load(@Nullable Path path) {
    if (path != null && !path.isAbsolute()) {
      LOG.info("unexpected relative path: " + path);
      path = null;
    }
    load(path, null, 0, true);
  }

  @Override
  public void reload(@Nullable Path focusOn) {
    if (focusOn == null) {
      FsItem value = myList.getSelectedValue();
      if (value != null) {
        focusOn = value.path;
      }
    }
    synchronized (myLock) {
      load(myCurrentDirectory, focusOn, 0, true);
    }
  }

  @Override
  public boolean showPathBar() {
    return myShowPathBar;
  }

  @Override
  public void showPathBar(boolean show) {
    myShowPathBar = show;
    PropertiesComponent.getInstance().setValue(FILE_CHOOSER_SHOW_PATH_PROPERTY, Boolean.toString(show));
    myPath.setVisible(show);
    (show ? myPath : myList).requestFocusInWindow();
  }

  @Override
  public boolean showHiddenFiles() {
    return myShowHiddenFiles;
  }

  @Override
  public void showHiddenFiles(boolean show) {
    myShowHiddenFiles = show;
    synchronized (myLock) {
      if (myCurrentDirectory != null) {
        var selection = myList.getSelectedValue();
        myModel.clear();
        for (var item : myCurrentContent) {
          if (show || item.visible) myModel.add(item);
        }
        myList.setSelectedValue(selection, true);
      }
    }
  }

  @Override
  public @Nullable Path currentDirectory() {
    return myCurrentDirectory;
  }

  @Override
  public @NotNull List<Path> selectedPaths() {
    return myList.getSelectedValuesList().stream()
      .filter(r -> !FsItem.UPLINK.equals(r.name) && r.path != null && r.path.getParent() != null)
      .map(r -> r.path)
      .collect(Collectors.toList());
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      cancelCurrentTask();
    }
    if (myWatcher != null) {
      try {
        myWatcher.close();
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
    myOpenFileSystems.forEach((p, fs) -> {
      try {
        fs.close();
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    });
  }

  private static final int UPPER_LEVEL = 1;
  private static final int INTO_ARCHIVE = 2;

  private void load(@Nullable Path path, @Nullable Path focusOn, int direction, boolean updatePathBar) {
    synchronized (myLock) {
      if (updatePathBar) {
        myPath.setItem(path != null ? new PathWrapper(path) : null);
      }

      myModel.clear();
      myList.clearSelection();
      myList.setPaintBusy(true);

      var childDir = direction == UPPER_LEVEL ? myCurrentDirectory : null;
      myCurrentDirectory = null;
      myCurrentContent.clear();
      cancelCurrentTask();
      var id = myCounter++;
      if (LOG.isTraceEnabled()) LOG.trace("starting: " + id + ", " + path);
      myCurrentTask = pair(id, execute(() -> {
        var directory = directoryToLoad(path, direction == INTO_ARCHIVE);
        if (directory != null) {
          var pathToSelect = focusOn != null ? focusOn :
                             childDir != null && childDir.getParent() == null && !isLocalFs(childDir) ? parent(childDir) :
                             childDir;
          loadDirectory(id, directory, pathToSelect, updatePathBar);
        }
        else {
          loadRoots(id, childDir, updatePathBar);
        }
      }));
    }
  }

  private void cancelCurrentTask() {
    myCurrentTask.second.cancel(true);
    if (myWatchKey != null) {
      myWatchKey.cancel();
      myWatchKey = null;
    }
  }

  private @Nullable Path directoryToLoad(@Nullable Path path, boolean asZip) {
    if (path != null && asZip) {
      try {
        @SuppressWarnings("resource") var fs = myOpenFileSystems.computeIfAbsent(path, k -> {
          try {
            return FileSystems.newFileSystem(path, (ClassLoader)null);
          }
          catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
        return fs.getRootDirectories().iterator().next();
      }
      catch (Exception e) {
        LOG.warn(e);
        return path.getParent();
      }
    }

    for (var current = path; current != null; current = parent(current)) {
      try {
        var attributes = Files.readAttributes(current, BasicFileAttributes.class);
        return attributes.isDirectory() ? current : current.getParent();
      }
      catch (Exception e) {
        LOG.trace(e);
      }
    }

    return null;
  }

  private void loadDirectory(int id, Path directory, @Nullable Path pathToSelect, boolean updatePathBar) {
    var cancelled = new AtomicBoolean(false);

    var vfsDirectory = new PreloadedDirectory(directory);
    var uplink = new FsItem(parent(directory), FsItem.UPLINK, true, true, myDescriptor.isFileSelectable(vfsDirectory), AllIcons.Nodes.UpFolder);
    update(id, cancelled, () -> {
      myCurrentDirectory = directory;
      if (updatePathBar) {
        myPath.setItem(new PathWrapper(directory));
      }
      myCurrentContent.add(uplink);
      myModel.add(uplink);
    });

    var selection = new AtomicReference<>(uplink);
    var error = new AtomicReference<String>();
    try {
      PlatformNioHelper.visitDirectory(directory, (file, result) -> {
        BasicFileAttributes attrs;
        try {
          attrs = result.get();
        }
        catch (Exception e) {
          error.set(e.getMessage());
          return true;
        }

        if (attrs.isSymbolicLink()) {
          try {
            attrs = new DelegatingFileAttributes(Files.readAttributes(file, BasicFileAttributes.class));
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }

        var virtualFile = new LazyDirectoryOrFile(vfsDirectory, file, attrs);
        if (!myDescriptor.isFileVisible(virtualFile, true)) {
          return true;  // not hidden, just ignored
        }
        var visible = myDescriptor.isFileVisible(virtualFile, false);
        var selectable = myDescriptor.isFileSelectable(virtualFile);
        var icon = myDescriptor.getIcon(virtualFile);
        var item = new FsItem(file, file.getFileName().toString(), attrs.isDirectory(), visible, selectable, icon);
        update(id, cancelled, () -> {
          myCurrentContent.add(item);
          if (visible || myShowHiddenFiles) {
            myModel.add(item);
          }
        });

        if (pathToSelect != null && file.equals(pathToSelect)) {
          selection.set(item);
        }

        return !cancelled.get();
      });
    }
    catch (IOException | RuntimeException e) {
      LOG.warn(directory.toString(), e);
      error.set(e.getMessage());
    }

    if (!cancelled.get()) {
      WatchKey watchKey = null;
      if (myWatcher != null && isLocalFs(directory)) {
        try {
          watchKey = directory.register(myWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
        }
        catch (Exception e) {
          if (LOG.isDebugEnabled()) LOG.debug("cannot watch " + directory, e);
        }
      }

      var _watchKey = watchKey;
      update(
        id,
        () -> {
          myList.setPaintBusy(false);
          myList.setSelectedValue(selection.get(), true);
          reportError("file.chooser.cannot.load.dir", error);
          myWatchKey = _watchKey;
        },
        () -> { if (_watchKey != null) _watchKey.cancel(); });
    }
  }

  private void loadRoots(int id, @Nullable Path pathToSelect, boolean updatePathBar) {
    var cancelled = new AtomicBoolean(false);

    update(id, cancelled, () -> {
      myCurrentDirectory = null;
      if (updatePathBar) {
        myPath.setItem(null);
      }
    });

    var roots = new ArrayList<Path>();
    for (var root : FileSystems.getDefault().getRootDirectories()) roots.add(root);
    if (WSLUtil.isSystemCompatible() && Experiments.getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser")) {
      try {
        List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributionsFuture().get(200, TimeUnit.MILLISECONDS);
        for (var distribution : distributions) roots.add(distribution.getUNCRootPath());
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }

    var selection = new AtomicReference<FsItem>();
    var error = new AtomicReference<String>();
    for (var root : roots) {
      if (cancelled.get()) break;
      try {
        var attrs = Files.readAttributes(root, BasicFileAttributes.class);
        var virtualFile = new LazyDirectoryOrFile(null, root, attrs);
        var name = NioFiles.getFileName(root);
        if (name.length() > 1 && name.endsWith(File.separator)) {
          name = name.substring(0, name.length() - 1);
        }
        if (SystemInfo.isWindows) {
          try {
            var store = Files.getFileStore(root).name();
            if (!store.isBlank()) {
              name += " [" + store + ']';
            }
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
        var item = new FsItem(root, name, true, true, myDescriptor.isFileSelectable(virtualFile), AllIcons.Nodes.Folder);
        update(id, cancelled, () -> myModel.add(item));
        if (pathToSelect != null && root.equals(pathToSelect)) {
          selection.set(item);
        }
      }
      catch (Exception e) {
        LOG.warn(root.toString(), e);
        error.set(e.getMessage());
      }
    }

    if (!cancelled.get()) {
      update(id, cancelled, () -> {
        myList.setPaintBusy(false);
        if (selection.get() != null) {
          myList.setSelectedValue(selection.get(), true);
        }
        else if (myModel.getSize() > 0) {
          myList.setSelectedIndex(0);
        }
        reportError("file.chooser.cannot.load.roots", error);
      });
    }
  }

  private static @Nullable Path parent(Path path) {
    var parent = path.getParent();
    if (parent == null && !isLocalFs(path)) {
      try {
        return Path.of(storeName(path));
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return parent;
  }

  private @Nullable Path findByPath(String text) {
    var p = text.lastIndexOf(SEPARATOR);
    if (p > 0 && myDescriptor.isChooseJarContents()) {
      var archive = NioFiles.toPath(text.substring(0, p));
      if (archive != null && myRegistry.getFileTypeByFileName(archive.getFileName().toString()) == ArchiveFileType.INSTANCE) {
        @SuppressWarnings("resource") var fs = myOpenFileSystems.computeIfAbsent(archive, k -> {
          try {
            return FileSystems.newFileSystem(archive, (ClassLoader)null);
          }
          catch (IOException e) {
            LOG.warn(e);
            return null;
          }
        });
        if (fs != null) {
          return fs.getRootDirectories().iterator().next().resolve(text.substring(p + 2));
        }
      }
    }

    return NioFiles.toPath(text);
  }

  private static boolean isLocalFs(Path file) {
    return file.getFileSystem() == FileSystems.getDefault();
  }

  // faster than `Files#getFileStore` (at least for ZipFS); not suitable for local FS
  private static String storeName(Path path) {
    return UriUtil.trimTrailingSlashes(path.getFileSystem().getFileStores().iterator().next().name());
  }

  private static Future<Void> execute(Runnable operation) {
    return CompletableFuture
      .runAsync(operation, ProcessIOExecutorService.INSTANCE)
      .exceptionally(t -> { LOG.error(t); return null; });
  }

  private void update(int id, AtomicBoolean cancelled, Runnable operation) {
    update(id, operation, () -> cancelled.set(true));
  }

  private void update(int id, Runnable whenActive, Runnable whenCancelled) {
    UIUtil.invokeLaterIfNeeded(() -> {
      synchronized (myLock) {
        boolean active = myCurrentTask.first == id && !myCurrentTask.second.isCancelled();
        (active ? whenActive : whenCancelled).run();
      }
    });
  }

  private void reportError(String key, AtomicReference<String> error) {
    String message = error.get();
    myErrorSink.accept(message != null ? UIBundle.message(key, message) : null);
  }

  private static final class PathWrapper {
    private final Path path;

    private PathWrapper(Path path) {
      this.path = path;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof FsItem && path.equals(((FsItem)o).path);
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }

    @Override
    public String toString() {
      if (!isLocalFs(path)) {
        try {
          var store = storeName(path);
          if (!store.isBlank()) {
            return store + '!' + path;
          }
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }
      return path.toString();
    }
  }

  private static final class FsItem {
    private static final String UPLINK = "..";

    private final Path path;
    private final @NlsSafe String name;
    private final boolean directory;
    private final boolean visible;
    private final boolean selectable;
    private final @Nullable Icon icon;

    private FsItem(Path path, String name, boolean directory, boolean visible, boolean selectable, @Nullable Icon icon) {
      this.path = path;
      this.name = name;
      this.directory = directory;
      this.visible = visible;
      this.selectable = selectable;
      this.icon = icon;
    }

    @Override
    public String toString() {
      return name;  // called by `JBList#doCopyToClipboardAction` and `ListSpeedSearch`
    }

    private static final Comparator<FsItem> COMPARATOR = (o1, o2) -> {
      if (UPLINK.equals(o1.name)) return -1;
      if (UPLINK.equals(o2.name)) return 1;
      var byType = Boolean.compare(o2.directory, o1.directory);
      if (byType != 0) return byType;
      byType = Boolean.compare(o1.name.startsWith("\\\\"), o2.name.startsWith("\\\\"));
      if (byType != 0) return byType;
      return NaturalComparator.INSTANCE.compare(o1.name, o2.name);
    };
  }

  private static final class MyListCellRenderer extends SimpleListCellRenderer<FsItem> {
    private final Border myPadding = JBUI.Borders.empty(UIUtil.getListCellVPadding(), 3);  // a constant from `DarculaComboBoxUI`

    @Override
    public void customize(@NotNull JList<? extends FsItem> list, FsItem value, int index, boolean selected, boolean focused) {
      setBorder(myPadding);
      setIcon(value.icon);
      setText(value.name);
      setForeground(selected ? NamedColorUtil.getListSelectionForeground(true) : UIUtil.getListForeground());
      setEnabled(value.selectable || FsItem.UPLINK.equals(value.name));
    }
  }

  private static final class PreloadedDirectory extends CoreLocalVirtualFile {
    private final List<LazyDirectoryOrFile> myChildren = new ArrayList<>();

    private PreloadedDirectory(Path file) {
      super(FS, file, true);
    }

    @Override
    public @Nullable VirtualFile getParent() {
      return null;
    }

    @Override
    public @Nullable VirtualFile findChild(@NotNull String name) {
      if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IllegalArgumentException(name);
      return ContainerUtil.find(myChildren, f -> name.equals(f.getName()));
    }

    @Override
    public VirtualFile[] getChildren() {
      return VfsUtilCore.toVirtualFileArray(myChildren);
    }
  }

  private static final class LazyDirectoryOrFile extends CoreLocalVirtualFile {
    private final @Nullable VirtualFile myParent;
    private final @Nullable Map<String, Optional<LazyDirectoryOrFile>> myChildren;

    private LazyDirectoryOrFile(@Nullable VirtualFile parent, Path file, BasicFileAttributes attrs) {
      super(FS, file, attrs);
      myParent = parent;
      myChildren = attrs.isDirectory() ? new HashMap<>() : null;
      if (parent instanceof PreloadedDirectory) {
        ((PreloadedDirectory)parent).myChildren.add(this);
      }
    }

    @Override
    public @Nullable VirtualFile getParent() {
      return myParent;
    }

    @Override
    public @Nullable VirtualFile findChild(@NotNull String name) {
      if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IllegalArgumentException(name);
      return myChildren == null ? null : myChildren.computeIfAbsent(name, k -> {
        try {
          var childFile = getFile().resolve(name);
          var attrs = Files.readAttributes(childFile, BasicFileAttributes.class);
          return Optional.of(new LazyDirectoryOrFile(this, childFile, attrs));
        }
        catch (Exception e) {
          LOG.trace(e);
          return Optional.empty();
        }
      }).orElse(null);
    }

    @Override
    public VirtualFile[] getChildren() {
      return myChildren == null
             ? VirtualFile.EMPTY_ARRAY
             : myChildren.values().stream().map(o -> o.orElse(null)).filter(Objects::nonNull).toArray(VirtualFile[]::new);
    }
  }

  private static final class DelegatingFileAttributes implements BasicFileAttributes {
    private final BasicFileAttributes myDelegate;

    private DelegatingFileAttributes(BasicFileAttributes delegate) {
      myDelegate = delegate;
    }

    @Override
    public FileTime lastModifiedTime() {
      return myDelegate.lastModifiedTime();
    }

    @Override
    public FileTime lastAccessTime() {
      return myDelegate.lastAccessTime();
    }

    @Override
    public FileTime creationTime() {
      return myDelegate.creationTime();
    }

    @Override
    public boolean isRegularFile() {
      return myDelegate.isRegularFile();
    }

    @Override
    public boolean isDirectory() {
      return myDelegate.isDirectory();
    }

    @Override
    public boolean isSymbolicLink() {
      return true;
    }

    @Override
    public boolean isOther() {
      return myDelegate.isOther();
    }

    @Override
    public long size() {
      return myDelegate.size();
    }

    @Override
    public Object fileKey() {
      return myDelegate.fileKey();
    }
  }
}
