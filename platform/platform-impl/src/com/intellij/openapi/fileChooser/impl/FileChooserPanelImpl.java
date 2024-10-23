// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.Formats;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.TableView;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PlatformNioHelper;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Transferable;
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
  private static final String PROJECT_DIR_DETECTION_PROPERTY = "idea.chooser.lookup.for.project.dirs";
  private static final String SEPARATOR = "!/";
  private static final CoreLocalFileSystem FS = new CoreLocalFileSystem();
  private static final String OPEN = "open";

  private final FileTypeRegistry myRegistry;
  private final FileChooserDescriptor myDescriptor;
  private final Runnable myCallback;
  private final Consumer<@Nullable @DialogMessage String> myErrorSink;
  private final @Nullable WatchService myWatcher;
  private final Map<Path, FileSystem> myOpenFileSystems;

  private final ComboBox<PathWrapper> myPath;
  private final ListTableModel<FsItem> myModel;
  private final TableView<FsItem> myList;
  private boolean myShowPathBar;
  private boolean myPathBarActive;
  private volatile boolean myShowHiddenFiles;
  private volatile boolean myDetectProjectDirectories;

  private final Object myLock = new String("file.chooser.panel.lock");
  private int myCounter = 0;
  private Pair<Integer, Future<?>> myCurrentTask = pair(-1, CompletableFuture.completedFuture(null));
  // guarded by `myLock` via `update()` callback, which the inspection doesn't detect
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private @Nullable Path myCurrentDirectory;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private final List<FsItem> myCurrentContent = new ArrayList<>();
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private @Nullable WatchKey myWatchKey;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private final List<@Nullable Path> myHistory = new ArrayList<>();
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private int myHistoryIndex = -1;  // points to the last added or used element
  private volatile boolean myReloadSuppressed = false;

  FileChooserPanelImpl(@NotNull FileChooserDescriptor descriptor,
                       @NotNull Runnable callback,
                       @NotNull Consumer<@Nullable @DialogMessage String> errorSink,
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
    myDetectProjectDirectories= PropertiesComponent.getInstance().getBoolean(PROJECT_DIR_DETECTION_PROPERTY, true);

    var label = new JLabel(descriptor.getDescription());

    var group = new DefaultActionGroup();
    for (var action : ((DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserToolbar")).getChildActionsOrStubs()) {
      group.addAction(action);
    }
    for (var action : ((DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserSettings")).getChildActionsOrStubs()) {
      group.addAction(action).setAsSecondary(true);
    }
    var toolBar = ActionManager.getInstance().createActionToolbar("FileChooserDialog", group, true);
    toolBar.setSecondaryActionsIcon(AllIcons.Actions.More, true);
    toolBar.setTargetComponent(this);

    myPath = new ComboBox<>(Stream.of(recentPaths).map(PathWrapper::new).toArray(PathWrapper[]::new));
    myPath.setUsePreferredSizeAsMinimum(false);
    setupPathBar();

    var nameColumn = new MyColumnInfo(UIBundle.message("file.chooser.column.name"), 0, FsItem.COMPARATOR) {
      @Override
      public String valueOf(FsItem item) {
        return item.name;
      }
    };
    var sizeColumn = new MyColumnInfo(UIBundle.message("file.chooser.column.size"), 12, Comparator.comparing(item -> item.size)) {
      @Override
      public String valueOf(FsItem item) {
        return item.directory ? "--" : Formats.formatFileSize(item.size);
      }
    };
    var dateColumn = new MyColumnInfo(UIBundle.message("file.chooser.column.date"), 15, Comparator.comparing(item -> item.lastUpdated)) {
      @Override
      public String valueOf(FsItem item) {
        return DateFormatUtil.formatPrettyDateTime(item.lastUpdated);
      }
    };
    myModel = new ListTableModel<>(nameColumn, sizeColumn, dateColumn);
    myList = new TableView<>(myModel);
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
            if (!events.isEmpty() && !myReloadSuppressed) {
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
    var filter = (FileLookup.LookupFilter)
      f -> myDescriptor.isFileVisible(new CoreLocalVirtualFile(FS, ((LocalFsFinder.IoFile)f).getFile()), myShowHiddenFiles);
    var ignored = new FileTextFieldImpl(pathEditor, finder, filter, FileChooserFactoryImpl.getMacroMap(), this) {
      @Override
      protected void setTextToFile(FileLookup.LookupFile file) {
        super.setTextToFile(file);
        var path = typedPath();
        if (path != null) {
          load(path, null, Set.of());
        }
      }
    };
    pathEditor.getActionMap().put(JTextField.notifyAction, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myPath.isPopupVisible()) {
          myPath.setPopupVisible(false);
        }
        load(typedPath(), null, Set.of());
      }
    });
  }

  private @Nullable Path typedPath() {
    var object = myPath.getEditor().getItem();
    if (object instanceof PathWrapper wrapper) {
      return wrapper.path;
    }
    if (object instanceof String str && !str.isBlank()) {
      var path = findByPath(FileUtil.expandUserHome(str.trim()));
      if (path != null && path.isAbsolute()) {
        return path;
      }
    }
    return null;
  }

  private void setupDirectoryView() {
    myList.getTableHeader().setDefaultRenderer(new MyHeaderCellRenderer(myList.getTableHeader().getDefaultRenderer()));
    myList.setDefaultRenderer(Object.class, new MyTableCellRenderer(myList.getDefaultRenderer(Object.class)));
    myList.resetDefaultFocusTraversalKeys();
    myList.setShowGrid(false);
    myList.setCellSelectionEnabled(false);
    myList.setColumnSelectionAllowed(false);
    myList.setRowSelectionAllowed(true);
    myList.setSelectionMode(myDescriptor.isChooseMultiple() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
    myList.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myPathBarActive = false;
      }
    });
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
          var idx = myList.rowAtPoint(e.getPoint());
          if (idx >= 0) {
            myList.setRowSelectionInterval(idx, idx);
            var action = myList.getActionMap().get(OPEN);
            if (action != null) action.actionPerformed(null);
            return true;
          }
        }
        return false;
      }
    }.installOn(myList, true);
    myList.getActionMap().put(OPEN, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        openSelectedRow();
      }
    });
    myList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), OPEN);
    TableSpeedSearch.installOn(myList);
    myList.getActionMap().put(TableActions.Left.ID, myList.getActionMap().get(TableActions.CtrlHome.ID));
    myList.getActionMap().put(TableActions.Right.ID, myList.getActionMap().get(TableActions.CtrlEnd.ID));
    myList.setTransferHandler(new TransferHandler(null) {
      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }

      @Override
      protected Transferable createTransferable(JComponent c) {
        var items = myList.getSelectedObjects();
        return items.isEmpty() ? null : new TextTransferable(items.stream().map(item -> item.name).collect(Collectors.joining("\n")));
      }
    });
  }

  private void openSelectedRow() {
    var selection = myList.getSelectedRows();
    if (selection.length == 1) {
      var item = myList.getRow(selection[0]);
      if (item.directory) {
        load(item.path, null, EnumSet.of(OpenFlags.UPDATE_PATH_BAR));
      }
      else if (myDescriptor.isChooseJarContents() && myRegistry.getFileTypeByFileName(item.name) == ArchiveFileType.INSTANCE) {
        load(item.path, null, EnumSet.of(OpenFlags.UPDATE_PATH_BAR, OpenFlags.INTO_ARCHIVE));
      }
      else {
        myCallback.run();
      }
    }
  }

  JComponent getPreferredFocusedComponent() {
    return myShowPathBar ? myPath : myList;
  }

  @NotNull List<@NotNull Path> chosenPaths() {
    if (myShowPathBar && myPathBarActive) {
      var path = typedPath();
      return path != null ? List.of(path) : List.of();
    }
    else if (myList.getSelectedObjects().isEmpty()) {
      return myCurrentDirectory != null ? List.of(myCurrentDirectory) : List.of();
    }
    else {
      return myList.getSelectedObjects().stream()
        .filter(r -> r.selectable)
        .map(r -> r.path)
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
    load(path, null, EnumSet.of(OpenFlags.UPDATE_PATH_BAR));
  }

  @Override
  public void loadParent() {
    if (myCurrentDirectory != null) {
      load(parent(myCurrentDirectory), null, EnumSet.of(OpenFlags.UPDATE_PATH_BAR, OpenFlags.UPPER_LEVEL));
    }
  }

  @Override
  public boolean hasHistory(boolean backward) {
    return !myHistory.isEmpty() && (backward ? myHistoryIndex > 0 : myHistoryIndex < myHistory.size() - 1);
  }

  @Override
  public void loadHistory(boolean backward) {
    var path = myHistory.get(backward ? --myHistoryIndex : ++myHistoryIndex);
    load(path, null, EnumSet.of(OpenFlags.UPDATE_PATH_BAR, OpenFlags.KEEP_HISTORY));
  }

  @Override
  public void reload(@Nullable Path focusOn) {
    if (focusOn == null) {
      var value = myList.getSelectedObject();
      if (value != null) {
        focusOn = value.path;
      }
    }
    synchronized (myLock) {
      load(myCurrentDirectory, focusOn, EnumSet.of(OpenFlags.RELOAD, OpenFlags.UPDATE_PATH_BAR, OpenFlags.KEEP_HISTORY));
    }
  }

  @Override
  public void reloadAfter(@SuppressWarnings("BoundedWildcard") @NotNull ThrowableComputable<@Nullable Path, IOException> task) throws IOException {
    try {
      myReloadSuppressed = true;
      reload(task.compute());
    }
    finally {
      myReloadSuppressed = false;
    }
  }

  @Override
  public boolean pathBar() {
    return myShowPathBar;
  }

  @Override
  public boolean togglePathBar() {
    myShowPathBar = !myShowPathBar;
    PropertiesComponent.getInstance().setValue(FILE_CHOOSER_SHOW_PATH_PROPERTY, Boolean.toString(myShowPathBar));
    myPath.setVisible(myShowPathBar);
    (myShowPathBar ? myPath : myList).requestFocusInWindow();
    return myShowPathBar;
  }

  @Override
  public boolean hiddenFiles() {
    return myShowHiddenFiles;
  }

  @Override
  public boolean toggleHiddenFiles() {
    synchronized (myLock) {
      myShowHiddenFiles = !myShowHiddenFiles;
      if (myCurrentDirectory != null) {
        var selection = myList.getSelectedObject();
        myModel.setItems(myShowHiddenFiles
                         ? new ArrayList<>(myCurrentContent)
                         : new ArrayList<>(ContainerUtil.filter(myCurrentContent, item -> item.visible)));
        myList.setSelection(selection != null ? List.of(selection) : List.of());
      }
      return myShowHiddenFiles;
    }
  }

  @Override
  public boolean projectDetection() {
    return myDetectProjectDirectories;
  }

  @Override
  public boolean toggleProjectDetection() {
    var newState = !myDetectProjectDirectories;
    myDetectProjectDirectories = newState;
    PropertiesComponent.getInstance().setValue(PROJECT_DIR_DETECTION_PROPERTY, newState);
    reload(null);
    return newState;
  }

  @Override
  public @Nullable Path currentDirectory() {
    return myCurrentDirectory;
  }

  @Override
  public @NotNull List<Path> selectedPaths() {
    return myList.getSelectedObjects().stream()
      .filter(r -> r.path != null && r.path.getParent() != null)
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

  private enum OpenFlags {
    UPPER_LEVEL, INTO_ARCHIVE, RELOAD, UPDATE_PATH_BAR, KEEP_HISTORY
  }

  private void load(@Nullable Path path, @Nullable Path focusOn, Set<OpenFlags> flags) {
    synchronized (myLock) {
      var updatePathBar = flags.contains(OpenFlags.UPDATE_PATH_BAR);
      var updateHistory = !flags.contains(OpenFlags.KEEP_HISTORY);

      if (updatePathBar) {
        myPath.setItem(path != null ? new PathWrapper(path) : null);
      }

      myModel.setItems(new ArrayList<>());
      myList.clearSelection();
      myList.setPaintBusy(true);

      var childDir = flags.contains(OpenFlags.UPPER_LEVEL) ? myCurrentDirectory : null;
      if (!flags.contains(OpenFlags.RELOAD)) {
        myCurrentDirectory = null;
      }
      myCurrentContent.clear();
      cancelCurrentTask();
      var id = myCounter++;
      if (LOG.isTraceEnabled()) LOG.trace("starting: " + id + ", " + path);
      myCurrentTask = pair(id, execute(() -> {
        var directory = directoryToLoad(path, flags.contains(OpenFlags.INTO_ARCHIVE));
        if (directory != null) {
          var pathToSelect = focusOn != null ? focusOn :
                             childDir != null && childDir.getParent() == null && !isLocalFs(childDir) ? parent(childDir) :
                             childDir;
          loadDirectory(id, directory, pathToSelect, updatePathBar, updateHistory);
        }
        else {
          loadRoots(id, childDir, updatePathBar, updateHistory);
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

  private void loadDirectory(int id, Path directory, @Nullable Path pathToSelect, boolean updatePathBar, boolean updateHistory) {
    var cancelled = new AtomicBoolean(false);

    var vfsDirectory = new PreloadedDirectory(directory);
    update(id, cancelled, () -> {
      myCurrentDirectory = directory;
      updatePathBarAndHistory(directory, updatePathBar, updateHistory);
    });

    var selection = new AtomicReference<FsItem>();
    var error = new AtomicReference<String>();
    try {
      PlatformNioHelper.visitDirectory(directory, null, (file, result) -> {
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
        var icon = myDetectProjectDirectories || !virtualFile.isDirectory() ? myDescriptor.getIcon(virtualFile) : AllIcons.Nodes.Folder;
        var item = new FsItem(file, file.getFileName().toString(), attrs, visible, selectable, icon);
        update(id, cancelled, () -> {
          myCurrentContent.add(item);
          if (visible || myShowHiddenFiles) {
            myModel.addRow(item);
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
          updateSelection(selection);
          reportError("file.chooser.cannot.load.dir", error);
          myWatchKey = _watchKey;
        },
        () -> { if (_watchKey != null) _watchKey.cancel(); });
    }
  }

  private void loadRoots(int id, @Nullable Path pathToSelect, boolean updatePathBar, boolean updateHistory) {
    var cancelled = new AtomicBoolean(false);

    update(id, cancelled, () -> {
      myCurrentDirectory = null;
      updatePathBarAndHistory(null, updatePathBar, updateHistory);
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
        var item = new FsItem(root, name, null, true, myDescriptor.isFileSelectable(virtualFile), AllIcons.Nodes.Folder);
        update(id, cancelled, () -> myModel.addRow(item));
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
        updateSelection(selection);
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
        var active = myCurrentTask.first == id && !myCurrentTask.second.isCancelled();
        (active ? whenActive : whenCancelled).run();
      }
    });
  }

  private void updatePathBarAndHistory(@Nullable Path path, boolean updatePathBar, boolean updateHistory) {
    if (updatePathBar) {
      myPath.setItem(path != null ? new PathWrapper(path) : null);
    }
    if (updateHistory) {
      myHistory.subList(++myHistoryIndex, myHistory.size()).clear();
      myHistory.add(path);
    }
  }

  private void updateSelection(AtomicReference<FsItem> selection) {
    if (selection.get() != null) {
      myList.setSelection(List.of(selection.get()));
    }
    else {
      myList.clearSelection();
    }
  }

  private void reportError(String key, AtomicReference<String> error) {
    var message = error.get();
    myErrorSink.accept(message != null ? UIBundle.message(key, message) : null);
  }

  private static final class PathWrapper {
    private final Path path;

    private PathWrapper(Path path) {
      this.path = path;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof FsItem item && path.equals(item.path);
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
    private final Path path;
    private final @NlsSafe String name;
    private final boolean directory;
    private final long size;
    private final long lastUpdated;
    private final boolean visible;
    private final boolean selectable;
    private final @Nullable Icon icon;

    private FsItem(Path path, String name, @Nullable BasicFileAttributes attrs, boolean visible, boolean selectable, @Nullable Icon icon) {
      this.path = path;
      this.name = name;
      this.directory = attrs == null || attrs.isDirectory();
      this.size = attrs == null || attrs.isDirectory() ? 0L : attrs.size();
      this.lastUpdated = attrs == null ? 0L : attrs.lastModifiedTime().toMillis();
      this.visible = visible;
      this.selectable = selectable;
      this.icon = icon;
    }

    private static final Comparator<FsItem> COMPARATOR = (o1, o2) -> {
      var byType = Boolean.compare(o2.directory, o1.directory);
      if (byType != 0) return byType;
      byType = Boolean.compare(o1.name.startsWith("\\\\"), o2.name.startsWith("\\\\"));
      if (byType != 0) return byType;
      return NaturalComparator.INSTANCE.compare(o1.name, o2.name);
    };
  }

  private abstract static class MyDelegatingTableCellRenderer implements TableCellRenderer {
    private final TableCellRenderer myDelegate;

    private MyDelegatingTableCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
      var component = myDelegate.getTableCellRendererComponent(table, value, selected, false, row, column);
      if (component instanceof JLabel label) {
        customizeComponent(table, row, column, label);
      }
      return component;
    }

    protected abstract void customizeComponent(JTable table, int row, int column, JLabel label);
  }

  private static final class MyHeaderCellRenderer extends MyDelegatingTableCellRenderer {
    private MyHeaderCellRenderer(TableCellRenderer delegate) {
      super(delegate);
    }

    @Override
    protected void customizeComponent(JTable table, int row, int column, JLabel label) {
      label.setHorizontalAlignment(SwingConstants.LEFT);
    }
  }

  private static final class MyTableCellRenderer extends MyDelegatingTableCellRenderer {
    private MyTableCellRenderer(TableCellRenderer delegate) {
      super(delegate);
    }

    @Override
    protected void customizeComponent(JTable table, int row, int column, JLabel label) {
      @SuppressWarnings("unchecked") var item = ((TableView<FsItem>)table).getRow(row);
      label.setIcon(column == 0 ? item.icon : null);
      label.setHorizontalAlignment(SwingConstants.LEFT);
      label.setToolTipText(column == 1 ? DateFormatUtil.formatDateTime(item.lastUpdated) : null);
      label.setEnabled(item.selectable);
    }
  }

  private abstract static class MyColumnInfo extends ColumnInfo<FsItem, String> {
    private final int myWidth;
    private final Comparator<FsItem> myComparator;

    private MyColumnInfo(@NlsContexts.ColumnName String name, int width, Comparator<FsItem> comparator) {
      super(name);
      myWidth = width;
      myComparator = comparator;
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth == 0 ? 0 : table.getFontMetrics(table.getFont()).charWidth('X') * myWidth;
    }

    @Override
    public Comparator<FsItem> getComparator() {
      return myComparator;
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
      if (parent instanceof PreloadedDirectory preloaded) {
        preloaded.myChildren.add(this);
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
