// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ProjectTopics;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.impl.DockManagerImpl;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutSettingsManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.impl.MessageListenerList;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Anton Katilin
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
@State(name = "FileEditorManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public class FileEditorManagerImpl extends FileEditorManagerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(FileEditorManagerImpl.class);
  protected static final Key<Boolean> DUMB_AWARE = Key.create("DUMB_AWARE");

  private static final FileEditorProvider[] EMPTY_PROVIDER_ARRAY = {};
  public static final Key<Boolean> CLOSING_TO_REOPEN = Key.create("CLOSING_TO_REOPEN");
  public static final String FILE_EDITOR_MANAGER = "FileEditorManager";

  private EditorsSplitters mySplitters;
  private final Project myProject;
  private final List<Pair<VirtualFile, EditorWindow>> mySelectionHistory = new ArrayList<>();
  private Reference<EditorComposite> myLastSelectedComposite = new WeakReference<>(null);

  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("FileEditorManagerUpdateQueue", 50, true,
                                                                    MergingUpdateQueue.ANY_COMPONENT, this);

  private final BusyObject.Impl.Simple myBusyObject = new BusyObject.Impl.Simple();

  /**
   * Removes invalid myEditor and updates "modified" status.
   */
  private final PropertyChangeListener myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();
  private final DockManager myDockManager;
  private DockableEditorContainerFactory myContentFactory;
  private static final AtomicInteger ourOpenFilesSetModificationCount = new AtomicInteger();

  static final ModificationTracker OPEN_FILE_SET_MODIFICATION_COUNT = ourOpenFilesSetModificationCount::get;
  private final List<EditorComposite> myOpenedEditors = new CopyOnWriteArrayList<>();

  private final MessageListenerList<FileEditorManagerListener> myListenerList;

  public FileEditorManagerImpl(@NotNull Project project) {
    myProject = project;
    myDockManager = DockManager.getInstance(myProject);
    myListenerList = new MessageListenerList<>(myProject.getMessageBus(), FileEditorManagerListener.FILE_EDITOR_MANAGER);

    if (FileEditorAssociateFinder.EP_NAME.hasAnyExtensions()) {
      myListenerList.add(new FileEditorManagerListener() {
        @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
          EditorsSplitters splitters = getSplitters();
          openAssociatedFile(event.getNewFile(), splitters.getCurrentWindow(), splitters);
        }
      });
    }

    myQueue.setTrackUiActivity(true);

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
        ApplicationManager.getApplication().invokeLater(() -> dumbModeFinished(myProject), myProject.getDisposed());
      }
    });
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        if (project == myProject) {
          FileEditorManagerImpl.this.projectOpened(connection);
        }
      }

      @Override
      public void projectClosing(@NotNull Project project) {
        if (project == myProject) {
          // Dispose created editors. We do not use use closeEditor method because
          // it fires event and changes history.
          closeAllFiles();
        }
      }
    });

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.addExtensionPointListener(new ExtensionPointListener<FileEditorProvider>() {
      @Override
      public void extensionRemoved(@NotNull FileEditorProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        for (EditorComposite editor : myOpenedEditors) {
          for (FileEditorProvider provider : editor.getProviders()) {
            if (provider.equals(extension)) {
              closeFile(editor.getFile());
              break;
            }
          }
        }
      }
    }, this);
  }

  @Override
  public void dispose() {
  }

  private void dumbModeFinished(Project project) {
    VirtualFile[] files = getOpenFiles();
    for (VirtualFile file : files) {
      Set<FileEditorProvider> providers = new HashSet<>();
      List<EditorWithProviderComposite> composites = getEditorComposites(file);
      for (EditorWithProviderComposite composite : composites) {
        ContainerUtil.addAll(providers, composite.getProviders());
      }
      FileEditorProvider[] newProviders = FileEditorProviderManager.getInstance().getProviders(project, file);
      List<FileEditorProvider> toOpen = new ArrayList<>(Arrays.asList(newProviders));
      toOpen.removeAll(providers);
      // need to open additional non dumb-aware editors
      for (EditorWithProviderComposite composite : composites) {
        for (FileEditorProvider provider : toOpen) {
          FileEditor editor = provider.createEditor(myProject, file);
          composite.addEditor(editor, provider);
        }
      }
    }

    // update for non-dumb-aware EditorTabTitleProviders
    updateFileName(null);
  }

  public void initDockableContentFactory() {
    if (myContentFactory != null) {
      return;
    }

    myContentFactory = new DockableEditorContainerFactory(myProject, this);
    myDockManager.register(DockableEditorContainerFactory.TYPE, myContentFactory, this);
  }

  public static boolean isDumbAware(@NotNull FileEditor editor) {
    return Boolean.TRUE.equals(editor.getUserData(DUMB_AWARE)) &&
           (!(editor instanceof PossiblyDumbAware) || ((PossiblyDumbAware)editor).isDumbAware());
  }

  //-------------------------------------------------------------------------------

  @Override
  public JComponent getComponent() {
    return initUI();
  }

  public @NotNull EditorsSplitters getMainSplitters() {
    return initUI();
  }

  public @NotNull Set<EditorsSplitters> getAllSplitters() {
    Set<EditorsSplitters> all = new LinkedHashSet<>();
    all.add(getMainSplitters());
    Set<DockContainer> dockContainers = myDockManager.getContainers();
    for (DockContainer each : dockContainers) {
      if (each instanceof DockableEditorTabbedContainer) {
        all.add(((DockableEditorTabbedContainer)each).getSplitters());
      }
    }
    return Collections.unmodifiableSet(all);
  }

  private @NotNull Promise<EditorsSplitters> getActiveSplittersAsync() {
    AsyncPromise<EditorsSplitters> result = new AsyncPromise<>();
    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    fm.doWhenFocusSettlesDown(() -> {
      if (myProject.isDisposed()) {
        result.cancel();
        return;
      }
      Component focusOwner = fm.getFocusOwner();
      DockContainer container = myDockManager.getContainerFor(focusOwner);
      if (container instanceof DockableEditorTabbedContainer) {
        result.setResult(((DockableEditorTabbedContainer)container).getSplitters());
      }
      else {
        result.setResult(getMainSplitters());
      }
    }, ModalityState.defaultModalityState());
    return result;
  }

  private EditorsSplitters getActiveSplittersSync() {
    assertDispatchThread();

    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component focusOwner = fm.getFocusOwner();
    if (focusOwner == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }
    if (focusOwner == null) {
      focusOwner = fm.getLastFocusedFor(fm.getLastFocusedIdeWindow());
    }

    DockContainer container = myDockManager.getContainerFor(focusOwner);
    if (container == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      container = myDockManager.getContainerFor(focusOwner);
    }

    if (container instanceof DockableEditorTabbedContainer) {
      return ((DockableEditorTabbedContainer)container).getSplitters();
    }
    return getMainSplitters();
  }

  private final Object myInitLock = new Object();

  private @NotNull EditorsSplitters initUI() {
    EditorsSplitters result = mySplitters;
    if (result != null) {
      return result;
    }

    synchronized (myInitLock) {
      result = mySplitters;
      if (result == null) {
        result = new EditorsSplitters(this, true, this);
        mySplitters = result;
      }
    }
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    assertReadAccess();
    EditorWindow window = getSplitters().getCurrentWindow();
    if (window != null) {
      EditorWithProviderComposite editor = window.getSelectedEditor();
      if (editor != null) {
        return editor.getPreferredFocusedComponent();
      }
    }
    return null;
  }

  //-------------------------------------------------------

  /**
   * @return color of the {@code file} which corresponds to the
   *         file's status
   */
  @NotNull
  Color getFileColor(@NotNull VirtualFile file) {
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    Color statusColor = fileStatusManager != null ? fileStatusManager.getStatus(file).getColor() : UIUtil.getLabelForeground();
    if (statusColor == null) statusColor = UIUtil.getLabelForeground();
    return statusColor;
  }

  public boolean isProblem(@NotNull VirtualFile file) {
    return false;
  }

  public @NotNull String getFileTooltipText(@NotNull VirtualFile file) {
    List<EditorTabTitleProvider> availableProviders = DumbService.getDumbAwareExtensions(myProject, EditorTabTitleProvider.EP_NAME);
    for (EditorTabTitleProvider provider : availableProviders) {
      String text = provider.getEditorTabTooltipText(myProject, file);
      if (text != null) {
        return text;
      }
    }
    return FileUtil.getLocationRelativeToUserHome(file.getPresentableUrl());
  }

  @Override
  public void updateFilePresentation(@NotNull VirtualFile file) {
    if (!isFileOpen(file)) return;

    updateFileName(file);
    queueUpdateFile(file);
  }

  /**
   * Updates tab color for the specified {@code file}. The {@code file}
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  private void updateFileColor(@NotNull VirtualFile file) {
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      each.updateFileColor(file);
    }
  }

  private void updateFileBackgroundColor(@NotNull VirtualFile file) {
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      each.updateFileBackgroundColor(file);
    }
  }

  /**
   * Updates tab icon for the specified {@code file}. The {@code file}
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  protected void updateFileIcon(@NotNull VirtualFile file) {
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      each.updateFileIcon(file);
    }
  }

  /**
   * Updates tab title and tab tool tip for the specified {@code file}
   */
  void updateFileName(@Nullable VirtualFile file) {
    // Queue here is to prevent title flickering when tab is being closed and two events arriving: with component==null and component==next focused tab
    // only the last event makes sense to handle
    myQueue.queue(new Update("UpdateFileName " + (file == null ? "" : file.getPath())) {
      @Override
      public boolean isExpired() {
        return myProject.isDisposed() || !myProject.isOpen() || (file == null ? super.isExpired() : !file.isValid());
      }

      @Override
      public void run() {
        for (EditorsSplitters each : getAllSplitters()) {
          each.updateFileName(file);
        }
      }
    });
  }

  private void updateFrameTitle() {
    getActiveSplittersAsync().onSuccess(splitters -> splitters.updateFileName(null));
  }

  @Override
  public VirtualFile getFile(@NotNull FileEditor editor) {
    EditorComposite editorComposite = getEditorComposite(editor);
    return editorComposite == null ? null : editorComposite.getFile();
  }

  @Override
  public void unsplitWindow() {
    EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplit(true);
    }
  }

  @Override
  public void unsplitAllWindow() {
    EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplitAll();
    }
  }

  @Override
  public int getWindowSplitCount() {
    return getActiveSplittersSync().getSplitCount();
  }

  @Override
  public boolean hasSplitOrUndockedWindows() {
    Set<EditorsSplitters> splitters = getAllSplitters();
    if (splitters.size() > 1) return true;
    return getWindowSplitCount() > 1;
  }

  @Override
  public EditorWindow @NotNull [] getWindows() {
    List<EditorWindow> windows = new ArrayList<>();
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      EditorWindow[] eachList = each.getWindows();
      ContainerUtil.addAll(windows, eachList);
    }

    return windows.toArray(new EditorWindow[0]);
  }

  @Override
  public EditorWindow getNextWindow(@NotNull EditorWindow window) {
    List<EditorWindow> windows = getSplitters().getOrderedWindows();
    for (int i = 0; i != windows.size(); ++i) {
      if (windows.get(i).equals(window)) {
        return windows.get((i + 1) % windows.size());
      }
    }
    LOG.error("Not window found");
    return null;
  }

  @Override
  public EditorWindow getPrevWindow(@NotNull EditorWindow window) {
    List<EditorWindow> windows = getSplitters().getOrderedWindows();
    for (int i = 0; i != windows.size(); ++i) {
      if (windows.get(i).equals(window)) {
        return windows.get((i + windows.size() - 1) % windows.size());
      }
    }
    LOG.error("Not window found");
    return null;
  }

  @Override
  public void createSplitter(int orientation, @Nullable EditorWindow window) {
    // window was available from action event, for example when invoked from the tab menu of an editor that is not the 'current'
    if (window != null) {
      window.split(orientation, true, null, false);
    }
    // otherwise we'll split the current window, if any
    else {
      EditorWindow currentWindow = getSplitters().getCurrentWindow();
      if (currentWindow != null) {
        currentWindow.split(orientation, true, null, false);
      }
    }
  }

  @Override
  public void changeSplitterOrientation() {
    EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.changeOrientation();
    }
  }

  @Override
  public boolean isInSplitter() {
    EditorWindow currentWindow = getSplitters().getCurrentWindow();
    return currentWindow != null && currentWindow.inSplitter();
  }

  @Override
  public boolean hasOpenedFile() {
    EditorWindow currentWindow = getSplitters().getCurrentWindow();
    return currentWindow != null && currentWindow.getSelectedEditor() != null;
  }

  @Override
  public VirtualFile getCurrentFile() {
    return getActiveSplittersSync().getCurrentFile();
  }

  @Override
  public @NotNull Promise<EditorWindow> getActiveWindow() {
    return getActiveSplittersAsync()
      .then(EditorsSplitters::getCurrentWindow);
  }

  @Override
  public EditorWindow getCurrentWindow() {
    if (!ApplicationManager.getApplication().isDispatchThread()) return null;
    EditorsSplitters splitters = getActiveSplittersSync();
    return splitters == null ? null : splitters.getCurrentWindow();
  }

  @Override
  public void setCurrentWindow(EditorWindow window) {
    getActiveSplittersSync().setCurrentWindow(window, true);
  }

  public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window, boolean transferFocus) {
    assertDispatchThread();
    ourOpenFilesSetModificationCount.incrementAndGet();

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      if (window.isFileOpen(file)) {
        window.closeFile(file, true, transferFocus);
      }
    }, IdeBundle.message("command.close.active.editor"), null);
    removeSelectionRecord(file, window);
  }

  @Override
  public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    closeFile(file, window, true);
  }

  //============================= EditorManager methods ================================

  @Override
  public void closeFile(@NotNull VirtualFile file) {
    closeFile(file, true, false);
  }

  public void closeFile(@NotNull VirtualFile file, boolean moveFocus, boolean closeAllCopies) {
    assertDispatchThread();

    CommandProcessor.getInstance().executeCommand(myProject, () -> closeFileImpl(file, moveFocus, closeAllCopies), "", null);
  }

  private void closeFileImpl(@NotNull VirtualFile file, boolean moveFocus, boolean closeAllCopies) {
    assertDispatchThread();
    ourOpenFilesSetModificationCount.incrementAndGet();
    runChange(splitters -> splitters.closeFile(file, moveFocus), closeAllCopies ? null : getActiveSplittersSync());
  }

  //-------------------------------------- Open File ----------------------------------------

  @Override
  public @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                 boolean focusEditor,
                                                                                 boolean searchForSplitter) {
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertDispatchThread();

    if (isOpenInNewWindow()) {
      return openFileInNewWindow(file);
    }


    EditorWindow wndToOpenIn = null;
    if (searchForSplitter && UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE) {
      Set<EditorsSplitters> all = getAllSplitters();
      EditorsSplitters active = getActiveSplittersSync();
      if (active.getCurrentWindow() != null && active.getCurrentWindow().isFileOpen(file)) {
        wndToOpenIn = active.getCurrentWindow();
      } else {
        for (EditorsSplitters splitters : all) {
          EditorWindow window = splitters.getCurrentWindow();
          if (window == null) continue;

          if (window.isFileOpen(file)) {
            wndToOpenIn = window;
            break;
          }
        }
      }
    }
    else {
      wndToOpenIn = getSplitters().getCurrentWindow();
    }

    EditorsSplitters splitters = getSplitters();

    if (wndToOpenIn == null) {
      wndToOpenIn = splitters.getOrCreateCurrentWindow(file);
    }

    openAssociatedFile(file, wndToOpenIn, splitters);
    return openFileImpl2(wndToOpenIn, file, focusEditor);
  }

  public Pair<FileEditor[], FileEditorProvider[]> openFileInNewWindow(@NotNull VirtualFile file) {
    return ((DockManagerImpl)DockManager.getInstance(getProject())).createNewDockContainerFor(file, this);
  }

  private static boolean isOpenInNewWindow() {
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();

    // Shift was used while clicking
    if (event instanceof MouseEvent &&
        ((MouseEvent)event).getModifiersEx() == InputEvent.SHIFT_DOWN_MASK &&
        (event.getID() == MouseEvent.MOUSE_CLICKED ||
         event.getID() == MouseEvent.MOUSE_PRESSED ||
         event.getID() == MouseEvent.MOUSE_RELEASED)) {
      return true;
    }

    if (event instanceof KeyEvent) {
      KeyEvent ke = (KeyEvent)event;
      Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
      String[] ids = keymap.getActionIds(KeyStroke.getKeyStroke(ke.getKeyCode(), ke.getModifiers()));
      return Arrays.asList(ids).contains("OpenElementInNewWindow");
    }

    return false;
  }

  private void openAssociatedFile(VirtualFile file, EditorWindow wndToOpenIn, @NotNull EditorsSplitters splitters) {
    EditorWindow[] windows = splitters.getWindows();

    if (file != null && windows.length == 2) {
      for (FileEditorAssociateFinder finder : FileEditorAssociateFinder.EP_NAME.getExtensionList()) {
        VirtualFile associatedFile = finder.getAssociatedFileToOpen(myProject, file);

        if (associatedFile != null) {
          EditorWindow currentWindow = splitters.getCurrentWindow();
          int idx = windows[0] == wndToOpenIn ? 1 : 0;
          openFileImpl2(windows[idx], associatedFile, false);

          if (currentWindow != null) {
            splitters.setCurrentWindow(currentWindow, false);
          }

          break;
        }
      }
    }
  }

  @Override
  public @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                 boolean focusEditor,
                                                                                 @NotNull EditorWindow window) {
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertDispatchThread();

    return openFileImpl2(window, file, focusEditor);
  }

  public @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(@NotNull EditorWindow window,
                                                                         @NotNull VirtualFile file,
                                                                         boolean focusEditor) {
    Ref<Pair<FileEditor[], FileEditorProvider[]>> result = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> result.set(openFileImpl3(window, file, focusEditor, null)), "", null);
    return result.get();
  }

  /**
   * @param file    to be opened. Unlike openFile method, file can be
   *                invalid. For example, all file were invalidate and they are being
   *                removed one by one. If we have removed one invalid file, then another
   *                invalid file become selected. That's why we do not require that
   *                passed file is valid.
   * @param entry   map between FileEditorProvider and FileEditorState. If this parameter
   */
  @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(@NotNull EditorWindow window,
                                                                 @NotNull VirtualFile file,
                                                                 boolean focusEditor,
                                                                 @Nullable HistoryEntry entry) {
    return openFileImpl4(window, file, entry, new FileEditorOpenOptions().withCurrentTab(true).withFocusEditor(focusEditor));
  }

  /**
   * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
   */
  protected @NotNull Pair<FileEditor @NotNull [], FileEditorProvider @NotNull []> openFileImpl4(@NotNull EditorWindow window,
                                                                                                @NotNull VirtualFile file,
                                                                                                @Nullable HistoryEntry entry,
                                                                                                @NotNull FileEditorOpenOptions options) {
    assert ApplicationManager.getApplication().isDispatchThread() || !ApplicationManager.getApplication().isReadAccessAllowed() : "must not open files under read action since we are doing a lot of invokeAndWaits here";

    Ref<EditorWithProviderComposite> compositeRef = new Ref<>();

    if (!options.isReopeningEditorsOnStartup()) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> compositeRef.set(window.findFileComposite(file)));
    }

    FileEditorProvider[] newProviders;
    AsyncFileEditorProvider.Builder[] builders;
    if (compositeRef.isNull()) {
      // File is not opened yet. In this case we have to create editors
      // and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProviders(myProject, file);
      if (newProviders.length == 0) {
        return Pair.createNonNull(FileEditor.EMPTY_ARRAY, EMPTY_PROVIDER_ARRAY);
      }

      builders = new AsyncFileEditorProvider.Builder[newProviders.length];
      for (int i = 0; i < newProviders.length; i++) {
        try {
          FileEditorProvider provider = newProviders[i];
          LOG.assertTrue(provider != null, "Provider for file "+file+" is null. All providers: "+Arrays.asList(newProviders));
          builders[i] = ReadAction.compute(() -> {
            if (myProject.isDisposed() || !file.isValid()) {
              return null;
            }
            LOG.assertTrue(provider.accept(myProject, file), "Provider " + provider + " doesn't accept file " + file);
            return provider instanceof AsyncFileEditorProvider ? ((AsyncFileEditorProvider)provider).createEditorAsync(myProject, file) : null;
          });
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception | AssertionError e) {
          LOG.error(e);
        }
      }
    }
    else {
      newProviders = null;
      builders = null;
    }

    ApplicationManager.getApplication().invokeAndWait(() -> {
      runBulkTabChange(window.getOwner(), splitters -> {
        openFileImpl4Edt(window, file, entry, options, compositeRef, newProviders, builders);
      });
    });

    EditorWithProviderComposite composite = compositeRef.get();
    return new Pair<>(composite == null ? FileEditor.EMPTY_ARRAY : composite.getEditors(),
                              composite == null ? EMPTY_PROVIDER_ARRAY : composite.getProviders());
  }

  private void openFileImpl4Edt(@NotNull EditorWindow window,
                                @NotNull VirtualFile file,
                                @Nullable HistoryEntry entry,
                                @NotNull FileEditorOpenOptions options,
                                @NotNull Ref<EditorWithProviderComposite> compositeRef,
                                FileEditorProvider [] newProviders,
                                AsyncFileEditorProvider.Builder [] builders) {
    if (myProject.isDisposed() || !file.isValid()) {
      return;
    }

    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

    compositeRef.set(window.findFileComposite(file));
    boolean newEditor = compositeRef.isNull();
    if (newEditor) {
      getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileOpened(this, file);

      FileEditor[] newEditors = new FileEditor[newProviders.length];
      for (int i = 0; i < newProviders.length; i++) {
        try {
          FileEditorProvider provider = newProviders[i];
          FileEditor editor = builders[i] == null ? provider.createEditor(myProject, file) : builders[i].build();
          LOG.assertTrue(editor.isValid(), "Invalid editor created by provider " +
                                            (provider == null ? null : provider.getClass().getName()));
          newEditors[i] = editor;
          // Register PropertyChangeListener into editor
          editor.addPropertyChangeListener(myEditorPropertyChangeListener);
          editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(provider));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception | AssertionError e) {
          LOG.error(e);
        }
      }

      // Now we have to create EditorComposite and insert it into the TabbedEditorComponent.
      // After that we have to select opened editor.
      EditorWithProviderComposite composite = createComposite(file, newEditors, newProviders);
      if (composite == null) return;

      if (options.getIndex() >= 0) {
        composite.getFile().putUserData(EditorWindow.INITIAL_INDEX_KEY, options.getIndex());
      }

      compositeRef.set(composite);
      myOpenedEditors.add(composite);
    }

    EditorWithProviderComposite composite = compositeRef.get();
    FileEditor[] editors = composite.getEditors();
    FileEditorProvider[] providers = composite.getProviders();

    window.setEditor(composite, options.isCurrentTab(), options.isFocusEditor());

    for (int i = 0; i < editors.length; i++) {
      restoreEditorState(file, providers[i], editors[i], entry, newEditor, options.isExactState());
    }

    // Restore selected editor
    FileEditorProvider selectedProvider;
    if (entry == null) {
      selectedProvider = ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance())
        .getSelectedFileEditorProvider(EditorHistoryManager.getInstance(myProject), file, providers);
    }
    else {
      selectedProvider = entry.getSelectedProvider();
    }
    if (selectedProvider != null) {
      for (int i = editors.length - 1; i >= 0; i--) {
        FileEditorProvider provider = providers[i];
        if (provider.equals(selectedProvider)) {
          composite.setSelectedEditor(i);
          break;
        }
      }
    }

    // Notify editors about selection changes
    window.getOwner().setCurrentWindow(window, options.isFocusEditor());
    window.getOwner().afterFileOpen(file);
    addSelectionRecord(file, window);

    composite.getSelectedEditor().selectNotify();

    // Transfer focus into editor
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (options.isFocusEditor()) {
        //myFirstIsActive = myTabbedContainer1.equals(tabbedContainer);
        window.setAsCurrentWindow(true);
        Window windowAncestor = SwingUtilities.getWindowAncestor(window.myPanel);
        if (windowAncestor != null &&
            windowAncestor.equals(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow())) {
          EditorsSplitters.focusDefaultComponentInSplittersIfPresent(myProject);
          IdeFocusManager.getInstance(myProject).toFront(window.getOwner());
        }
      }
    }

    if (newEditor) {
      ourOpenFilesSetModificationCount.incrementAndGet();
    }

    //[jeka] this is a hack to support back-forward navigation
    // previously here was incorrect call to fireSelectionChanged() with a side-effect
    ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();

    // Update frame and tab title
    updateFileName(file);

    // Make back/forward work
    IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();

    if (options.getPin() != null) {
      window.setFilePinned(file, options.getPin());
    }

    if (newEditor) {
      getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
        .fileOpenedSync(this, file, Pair.pair(editors, providers));

      notifyPublisher(() -> {
        if (isFileOpen(file)) {
          getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
            .fileOpened(this, file);
        }
      });
    }
  }

  protected final @Nullable EditorWithProviderComposite createComposite(@NotNull VirtualFile file,
                                                                        FileEditor @NotNull [] editors,
                                                                        FileEditorProvider @NotNull [] providers) {
    if (ArrayUtil.contains(null, editors) || ArrayUtil.contains(null, providers)) {
      List<FileEditor> editorList = new ArrayList<>(editors.length);
      List<FileEditorProvider> providerList = new ArrayList<>(providers.length);
      for (int i = 0; i < editors.length; i++) {
        FileEditor editor = editors[i];
        FileEditorProvider provider = providers[i];
        if (editor != null && provider != null) {
          editorList.add(editor);
          providerList.add(provider);
        }
      }
      if (editorList.isEmpty()) return null;
      editors = editorList.toArray(FileEditor.EMPTY_ARRAY);
      providers = providerList.toArray(new FileEditorProvider[0]);
    }
    return new EditorWithProviderComposite(file, editors, providers, this);
  }

  private void restoreEditorState(@NotNull VirtualFile file,
                                  @NotNull FileEditorProvider provider,
                                  @NotNull FileEditor editor,
                                  HistoryEntry entry,
                                  boolean newEditor,
                                  boolean exactState) {
    FileEditorState state = null;
    if (entry != null) {
      state = entry.getState(provider);
    }
    if (state == null && newEditor) {
      // We have to try to get state from the history only in case
      // if editor is not opened. Otherwise history entry might have a state
      // out of sync with the current editor state.
      state = EditorHistoryManager.getInstance(myProject).getState(file, provider);
    }
    if (state != null) {
      if (!isDumbAware(editor)) {
        FileEditorState finalState = state;
        DumbService.getInstance(getProject()).runWhenSmart(() -> editor.setState(finalState, exactState));
      }
      else {
        editor.setState(state, exactState);
      }
    }
  }

  @Override
  public @NotNull ActionCallback notifyPublisher(@NotNull Runnable runnable) {
    IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    ActionCallback done = new ActionCallback();
    return myBusyObject.execute(new ActiveRunnable() {
      @Override
      public @NotNull ActionCallback run() {
        focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(myProject, () -> {
          runnable.run();
          done.setDone();
        }), ModalityState.current());
        return done;
      }
    });
  }

  @Override
  public void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite == null) {
      List<EditorWithProviderComposite> composites = getEditorComposites(file);

      if (composites.isEmpty()) return;
      composite = composites.get(0);
    }

    FileEditorProvider[] editorProviders = composite.getProviders();
    FileEditorProvider selectedProvider = composite.getSelectedWithProvider().getProvider();

    for (int i = 0; i < editorProviders.length; i++) {
      if (editorProviders[i].getEditorTypeId().equals(fileEditorProviderId) && !selectedProvider.equals(editorProviders[i])) {
        composite.setSelectedEditor(i);
        composite.getSelectedEditor().selectNotify();
      }
    }
  }


  @Nullable
  EditorWithProviderComposite newEditorComposite(@Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }

    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
    if (providers.length == 0) return null;
    FileEditor[] editors = new FileEditor[providers.length];
    for (int i = 0; i < providers.length; i++) {
      FileEditorProvider provider = providers[i];
      LOG.assertTrue(provider != null);
      LOG.assertTrue(provider.accept(myProject, file));
      FileEditor editor = provider.createEditor(myProject, file);
      editors[i] = editor;
      LOG.assertTrue(editor.isValid());
      editor.addPropertyChangeListener(myEditorPropertyChangeListener);
    }

    EditorWithProviderComposite newComposite = new EditorWithProviderComposite(file, editors, providers, this);
    EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(myProject);
    for (int i = 0; i < editors.length; i++) {
      FileEditor editor = editors[i];

      FileEditorProvider provider = providers[i];

// Restore myEditor state
      FileEditorState state = editorHistoryManager.getState(file, provider);
      if (state != null) {
        editor.setState(state);
      }
    }
    return newComposite;
  }

  @Override
  public @NotNull List<FileEditor> openEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    return openEditorImpl(descriptor, focusEditor).first;
  }

  /**
   * @return the list of opened editors, and the one of them that was selected (if any)
   */
  private Pair<List<FileEditor>, FileEditor> openEditorImpl(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    assertDispatchThread();
    OpenFileDescriptor realDescriptor;
    if (descriptor.getFile() instanceof VirtualFileWindow) {
      VirtualFileWindow delegate = (VirtualFileWindow)descriptor.getFile();
      int hostOffset = delegate.getDocumentWindow().injectedToHost(descriptor.getOffset());
      realDescriptor = new OpenFileDescriptor(descriptor.getProject(), delegate.getDelegate(), hostOffset);
      realDescriptor.setUseCurrentWindow(descriptor.isUseCurrentWindow());
    }
    else {
      realDescriptor = descriptor;
    }

    List<FileEditor> result = new SmartList<>();
    Ref<FileEditor> selectedEditor = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      VirtualFile file = realDescriptor.getFile();
      FileEditor[] editors = openFile(file, focusEditor, !realDescriptor.isUseCurrentWindow());
      ContainerUtil.addAll(result, editors);

      boolean navigated = false;
      for (FileEditor editor : editors) {
        if (editor instanceof NavigatableFileEditor &&
            getSelectedEditor(realDescriptor.getFile()) == editor) { // try to navigate opened editor
          navigated = navigateAndSelectEditor((NavigatableFileEditor)editor, realDescriptor);
          if (navigated) {
            selectedEditor.set(editor);
            break;
          }
        }
      }

      if (!navigated) {
        for (FileEditor editor : editors) {
          if (editor instanceof NavigatableFileEditor && getSelectedEditor(realDescriptor.getFile()) != editor) { // try other editors
            if (navigateAndSelectEditor((NavigatableFileEditor)editor, realDescriptor)) {
              selectedEditor.set(editor);
              break;
            }
          }
        }
      }
    }, "", null);

    return Pair.create(result, selectedEditor.get());
  }

  private boolean navigateAndSelectEditor(@NotNull NavigatableFileEditor editor, @NotNull OpenFileDescriptor descriptor) {
    if (editor.canNavigateTo(descriptor)) {
      setSelectedEditor(editor);
      editor.navigateTo(descriptor);
      return true;
    }

    return false;
  }

  private void setSelectedEditor(@NotNull FileEditor editor) {
    EditorWithProviderComposite composite = getEditorComposite(editor);
    if (composite == null) return;

    FileEditor[] editors = composite.getEditors();
    for (int i = 0; i < editors.length; i++) {
      FileEditor each = editors[i];
      if (editor == each) {
        composite.setSelectedEditor(i);
        composite.getSelectedEditor().selectNotify();
        break;
      }
    }
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @Nullable Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    TextEditor textEditor = doOpenTextEditor(descriptor, focusEditor);
    return textEditor == null ? null : textEditor.getEditor();
  }

  private @Nullable TextEditor doOpenTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    Pair<List<FileEditor>, FileEditor> editorsWithSelected = openEditorImpl(descriptor, focusEditor);
    Collection<FileEditor> fileEditors = editorsWithSelected.first;
    FileEditor selectedEditor = editorsWithSelected.second;

    if (fileEditors.isEmpty()) return null;
    else if (fileEditors.size() == 1) return ObjectUtils.tryCast(ContainerUtil.getFirstItem(fileEditors), TextEditor.class);

    List<TextEditor> textEditors = ContainerUtil.mapNotNull(fileEditors, e -> ObjectUtils.tryCast(e, TextEditor.class));
    if (textEditors.isEmpty()) return null;

    TextEditor target = selectedEditor instanceof TextEditor ? (TextEditor)selectedEditor : textEditors.get(0);
    if (textEditors.size() > 1) {
      EditorWithProviderComposite composite = getEditorComposite(target);
      assert composite != null;
      FileEditor[] editors = composite.getEditors();
      FileEditorProvider[] providers = composite.getProviders();
      String textProviderId = TextEditorProvider.getInstance().getEditorTypeId();
      for (int i = 0; i < editors.length; i++) {
        FileEditor editor = editors[i];
        if (editor instanceof TextEditor && providers[i].getEditorTypeId().equals(textProviderId)) {
          target = (TextEditor)editor;
          break;
        }
      }
    }
    setSelectedEditor(target);
    return target;
  }

  @Override
  public Editor getSelectedTextEditor() {
    return getSelectedTextEditor(false);
  }

  public Editor getSelectedTextEditor(boolean lockfree) {
    if (!lockfree) {
      assertDispatchThread();
    }

    EditorWindow currentWindow = lockfree ? getMainSplitters().getCurrentWindow() : getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      EditorWithProviderComposite selectedEditor = currentWindow.getSelectedEditor();
      if (selectedEditor != null && selectedEditor.getSelectedEditor() instanceof TextEditor) {
        return ((TextEditor)selectedEditor.getSelectedEditor()).getEditor();
      }
    }

    return null;
  }

  @Override
  public boolean isFileOpen(@NotNull VirtualFile file) {
    for (EditorComposite editor : myOpenedEditors) {
      if (editor.getFile().equals(file)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public VirtualFile @NotNull [] getOpenFiles() {
    Set<VirtualFile> files = new LinkedHashSet<>();
    for (EditorComposite composite : myOpenedEditors) {
      files.add(composite.getFile());
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public boolean hasOpenFiles() {
    return !myOpenedEditors.isEmpty();
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    Set<VirtualFile> selectedFiles = new LinkedHashSet<>();
    EditorsSplitters activeSplitters = getSplitters();
    ContainerUtil.addAll(selectedFiles, activeSplitters.getSelectedFiles());
    for (EditorsSplitters each : getAllSplitters()) {
      if (each != activeSplitters) {
        ContainerUtil.addAll(selectedFiles, each.getSelectedFiles());
      }
    }
    return VfsUtilCore.toVirtualFileArray(selectedFiles);
  }

  @Override
  public FileEditor @NotNull [] getSelectedEditors() {
    Set<FileEditor> selectedEditors = new SmartHashSet<>();
    for (EditorsSplitters splitters : getAllSplitters()) {
      splitters.addSelectedEditorsTo(selectedEditors);
    }
    return selectedEditors.toArray(FileEditor.EMPTY_ARRAY);
  }

  @Override
  public @NotNull EditorsSplitters getSplitters() {
    EditorsSplitters active = null;
    if (ApplicationManager.getApplication().isDispatchThread()) active = getActiveSplittersSync();
    return active == null ? getMainSplitters() : active;
  }

  @Override
  public @Nullable FileEditor getSelectedEditor() {
    EditorWindow window = getSplitters().getCurrentWindow();
    if (window != null) {
      EditorComposite selected = window.getSelectedEditor();
      if (selected != null) return selected.getSelectedEditor();
    }
    return super.getSelectedEditor();
  }

  @Override
  public @Nullable FileEditor getSelectedEditor(@NotNull VirtualFile file) {
    FileEditorWithProvider editorWithProvider = getSelectedEditorWithProvider(file);
    return editorWithProvider == null ? null : editorWithProvider.getFileEditor();
  }


  @Override
  public @Nullable FileEditorWithProvider getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    file = BackedVirtualFile.getOriginFileIfBacked(file);
    EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return composite.getSelectedWithProvider();
    }

    List<EditorWithProviderComposite> composites = getEditorComposites(file);
    return composites.isEmpty() ? null : composites.get(0).getSelectedWithProvider();
  }

  @Override
  public @NotNull Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return new Pair<>(composite.getEditors(), composite.getProviders());
    }

    List<EditorWithProviderComposite> composites = getEditorComposites(file);
    if (!composites.isEmpty()) {
      return new Pair<>(composites.get(0).getEditors(), composites.get(0).getProviders());
    }
    return new Pair<>(FileEditor.EMPTY_ARRAY, EMPTY_PROVIDER_ARRAY);
  }

  @Override
  public FileEditor @NotNull [] getEditors(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    file = BackedVirtualFile.getOriginFileIfBacked(file);

    EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return composite.getEditors();
    }

    List<EditorWithProviderComposite> composites = getEditorComposites(file);
    if (!composites.isEmpty()) {
      return composites.get(0).getEditors();
    }
    return FileEditor.EMPTY_ARRAY;
  }

  @Override
  public FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file) {
    List<FileEditor> result = new ArrayList<>();
    myOpenedEditors.forEach(composite -> {
      if (composite.getFile().equals(file)) ContainerUtil.addAll(result, composite.myEditors);
    });
    return result.toArray(FileEditor.EMPTY_ARRAY);
  }

  private @Nullable EditorWithProviderComposite getCurrentEditorWithProviderComposite(@NotNull VirtualFile virtualFile) {
    EditorWindow editorWindow = getSplitters().getCurrentWindow();
    if (editorWindow != null) {
      return editorWindow.findFileComposite(virtualFile);
    }
    return null;
  }

  private @NotNull List<EditorWithProviderComposite> getEditorComposites(@NotNull VirtualFile file) {
    List<EditorWithProviderComposite> result = new ArrayList<>();
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      result.addAll(each.findEditorComposites(file));
    }
    return result;
  }

  @Override
  public FileEditor @NotNull [] getAllEditors() {
    List<FileEditor> result = new ArrayList<>();
    myOpenedEditors.forEach(composite -> ContainerUtil.addAll(result, composite.myEditors));
    return result.toArray(FileEditor.EMPTY_ARRAY);
  }


  public @NotNull List<JComponent> getTopComponents(@NotNull FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getEditorComposite(editor);
    return composite != null ? composite.getTopComponents(editor) : Collections.emptyList();
  }

  @Override
  public void addTopComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.addTopComponent(editor, component);
    }
  }

  @Override
  public void removeTopComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.removeTopComponent(editor, component);
    }
  }

  @Override
  public void addBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.addBottomComponent(editor, component);
    }
  }

  @Override
  public void removeBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.removeBottomComponent(editor, component);
    }
  }

  @Override
  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
    myListenerList.add(listener);
  }

  @Override
  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, @NotNull Disposable parentDisposable) {
    myProject.getMessageBus().connect(parentDisposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
  }

  @Override
  public void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
    myListenerList.remove(listener);
  }

  protected void projectOpened(@NotNull MessageBusConnection connection) {
    //myFocusWatcher.install(myWindows.getComponent ());
    getMainSplitters().startListeningFocus();

    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      // updates tabs colors
      fileStatusManager.addFileStatusListener(new MyFileStatusListener(), myProject);
    }
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootsListener());

    // updates tabs names
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new MyVirtualFileListener());

    // extends/cuts number of opened tabs. Also updates location of tabs
    connection.subscribe(UISettingsListener.TOPIC, new MyUISettingsListener());

    StartupManager.getInstance(myProject).runAfterOpened(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> CommandProcessor.getInstance().executeCommand(myProject, () -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          Long startTime = myProject.getUserData(ProjectImpl.CREATION_TIME);
          if (startTime != null) {
            long time = TimeoutUtil.getDurationMillis(startTime.longValue());
            LifecycleUsageTriggerCollector.onProjectOpenFinished(myProject, time);

            LOG.info("Project opening took " + time + " ms");
          }
        }, myProject.getDisposed());
        // group 1
      }, "", null), myProject.getDisposed());
    });
  }

  @Override
  public @Nullable Element getState() {
    if (mySplitters == null) {
      // do not save if not initialized yet
      return null;
    }

    Element state = new Element("state");
    getMainSplitters().writeExternal(state);
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    getMainSplitters().readExternal(state);
  }

  protected  @Nullable EditorWithProviderComposite getEditorComposite(@NotNull FileEditor editor) {
    for (EditorsSplitters splitters : getAllSplitters()) {
      List<EditorWithProviderComposite> editorsComposites = splitters.getEditorComposites();
      for (int i = editorsComposites.size() - 1; i >= 0; i--) {
        EditorWithProviderComposite composite = editorsComposites.get(i);
        FileEditor[] editors = composite.getEditors();
        for (int j = editors.length - 1; j >= 0; j--) {
          FileEditor _editor = editors[j];
          LOG.assertTrue(_editor != null);
          if (editor.equals(_editor)) {
            return composite;
          }
        }
      }
    }
    return null;
  }

  private static void assertDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  public void fireSelectionChanged(@Nullable EditorComposite newSelectedComposite) {
    Trinity<VirtualFile, FileEditor, FileEditorProvider> oldData = extract(SoftReference.dereference(myLastSelectedComposite));
    Trinity<VirtualFile, FileEditor, FileEditorProvider> newData = extract(newSelectedComposite);
    myLastSelectedComposite = newSelectedComposite == null ? null : new WeakReference<>(newSelectedComposite);
    boolean filesEqual = Objects.equals(oldData.first, newData.first);
    boolean editorsEqual = Objects.equals(oldData.second, newData.second);
    if (!filesEqual || !editorsEqual) {
      if (oldData.first != null && newData.first != null) {
        for (FileEditorAssociateFinder finder : FileEditorAssociateFinder.EP_NAME.getExtensionList()) {
          VirtualFile associatedFile = finder.getAssociatedFileToOpen(myProject, oldData.first);

          if (Comparing.equal(associatedFile, newData.first)) {
            return;
          }
        }
      }

      FileEditorManagerEvent event =
        new FileEditorManagerEvent(this, oldData.first, oldData.second, oldData.third, newData.first, newData.second, newData.third);
      FileEditorManagerListener publisher = getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);

      if (newData.first != null) {
        JComponent component = newData.second.getComponent();
        EditorWindowHolder holder =
          ComponentUtil.getParentOfType((Class<? extends EditorWindowHolder>)EditorWindowHolder.class, (Component)component);
        if (holder != null) {
          addSelectionRecord(newData.first, holder.getEditorWindow());
        }
      }
      notifyPublisher(() -> publisher.selectionChanged(event));
    }
  }

  private static @NotNull Trinity<VirtualFile, FileEditor, FileEditorProvider> extract(@Nullable EditorComposite composite) {
    VirtualFile file;
    FileEditor editor;
    FileEditorProvider provider;
    if (composite == null) {
      file = null;
      editor = null;
      provider = null;
    }
    else {
      file = composite.getFile();
      FileEditorWithProvider pair = composite.getSelectedWithProvider();
      editor = pair.getFileEditor();
      provider = pair.getProvider();
    }
    return new Trinity<>(file, editor, provider);
  }

  @Override
  public boolean isChanged(@NotNull EditorComposite editor) {
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager == null) {
      return false;
    }
    FileStatus status = fileStatusManager.getStatus(editor.getFile());
    return status != FileStatus.UNKNOWN && status != FileStatus.NOT_CHANGED;
  }

  void disposeComposite(@NotNull EditorWithProviderComposite editor) {
    myOpenedEditors.remove(editor);

    if (getAllEditors().length == 0) {
      setCurrentWindow(null);
    }

    if (editor.equals(getLastSelected())) {
      editor.getSelectedEditor().deselectNotify();
      getSplitters().setCurrentWindow(null, false);
    }

    FileEditor[] editors = editor.getEditors();
    FileEditorProvider[] providers = editor.getProviders();

    FileEditor selectedEditor = editor.getSelectedEditor();
    for (int i = editors.length - 1; i >= 0; i--) {
      FileEditor editor1 = editors[i];
      FileEditorProvider provider = providers[i];
      // we already notified the myEditor (when fire event)
      if (selectedEditor.equals(editor1)) {
        editor1.deselectNotify();
      }
      editor1.removePropertyChangeListener(myEditorPropertyChangeListener);
      provider.disposeEditor(editor1);
    }

    Disposer.dispose(editor);
  }

  private @Nullable EditorComposite getLastSelected() {
    EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
    if (currentWindow != null) {
      return currentWindow.getSelectedEditor();
    }
    return null;
  }

  /**
   * @param splitters - taken getAllSplitters() value if parameter is null
   */
  private void runChange(@NotNull FileEditorManagerChange change, @Nullable EditorsSplitters splitters) {
    Set<EditorsSplitters> target = new HashSet<>();
    if (splitters == null) {
      target.addAll(getAllSplitters());
    }
    else {
      target.add(splitters);
    }

    for (EditorsSplitters each : target) {
      runBulkTabChange(each, change);
    }
  }

  static void runBulkTabChange(@NotNull EditorsSplitters splitters, @NotNull FileEditorManagerChange change) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      change.run(splitters);
    }
    else {
      splitters.myInsideChange++;
      try {
        change.run(splitters);
      }
      finally {
        splitters.myInsideChange--;

        if (!splitters.isInsideChange()) {
          splitters.validate();
          for (EditorWindow window : splitters.getWindows()) {
            ((JBTabsImpl)window.getTabbedPane().getTabs()).revalidateAndRepaint();
          }
        }
      }
    }
  }

  /**
   * Closes deleted files. Closes file which are in the deleted directories.
   */
  private final class MyVirtualFileListener implements BulkFileListener {
    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          beforeFileDeletion((VFileDeleteEvent)event);
        }
      }
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent event : events) {
        if (event instanceof VFilePropertyChangeEvent) {
          propertyChanged((VFilePropertyChangeEvent)event);
        }
        else if (event instanceof VFileMoveEvent) {
          fileMoved((VFileMoveEvent)event);
        }
      }
    }

    private void beforeFileDeletion(@NotNull VFileDeleteEvent event) {
      assertDispatchThread();

      VirtualFile file = event.getFile();
      VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        if (VfsUtilCore.isAncestor(file, openFiles[i], false)) {
          closeFile(openFiles[i],true, true);
        }
      }
    }

    private void propertyChanged(@NotNull VFilePropertyChangeEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        assertDispatchThread();
        VirtualFile file = event.getFile();
        if (isFileOpen(file)) {
          updateFileName(file);
          updateFileIcon(file); // file type can change after renaming
          updateFileBackgroundColor(file);
        }
      }
      else if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName()) || VirtualFile.PROP_ENCODING.equals(event.getPropertyName())) {
        updateIcon(event);
      }
    }

    private void updateIcon(@NotNull VFilePropertyChangeEvent event) {
      assertDispatchThread();
      VirtualFile file = event.getFile();
      if (isFileOpen(file)) {
        updateFileIcon(file);
      }
    }

    private void fileMoved(@NotNull VFileMoveEvent e) {
      VirtualFile file = e.getFile();
      for (VirtualFile openFile : getOpenFiles()) {
        if (VfsUtilCore.isAncestor(file, openFile, false)) {
          updateFileName(openFile);
          updateFileBackgroundColor(openFile);
        }
      }
    }
  }

  @Override
  public boolean isInsideChange() {
    return getSplitters().isInsideChange();
  }

  private final class MyEditorPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(@NotNull PropertyChangeEvent e) {
      assertDispatchThread();

      String propertyName = e.getPropertyName();
      if (FileEditor.PROP_MODIFIED.equals(propertyName)) {
        FileEditor editor = (FileEditor)e.getSource();
        EditorComposite composite = getEditorComposite(editor);
        if (composite != null) {
          updateFileIcon(composite.getFile());
        }
      }
      else if (FileEditor.PROP_VALID.equals(propertyName)) {
        boolean valid = ((Boolean)e.getNewValue()).booleanValue();
        if (!valid) {
          FileEditor editor = (FileEditor)e.getSource();
          LOG.assertTrue(editor != null);
          EditorComposite composite = getEditorComposite(editor);
          if (composite != null) {
            closeFile(composite.getFile());
          }
        }
      }

    }
  }


  /**
   * Gets events from VCS and updates color of myEditor tabs
   */
  private final class MyFileStatusListener implements FileStatusListener {
    @Override
    public void fileStatusesChanged() { // update color of all open files
      assertDispatchThread();
      LOG.debug("FileEditorManagerImpl.MyFileStatusListener.fileStatusesChanged()");
      VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (LOG.isDebugEnabled()) {
            LOG.debug("updating file status in tab for " + file.getPath());
          }
          updateFileStatus(file);
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    }

    @Override
    public void fileStatusChanged(@NotNull VirtualFile file) { // update color of the file (if necessary)
      assertDispatchThread();
      if (isFileOpen(file)) {
        updateFileStatus(file);
      }
    }

    private void updateFileStatus(VirtualFile file) {
      updateFileColor(file);
      updateFileIcon(file);
    }
  }

  /**
   * Gets events from FileTypeManager and updates icons on tabs
   */
  private final class MyFileTypeListener implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull FileTypeEvent event) {
      assertDispatchThread();
      VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        updateFileIcon(file);
      }
    }
  }

  private class MyRootsListener implements ModuleRootListener {

    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      AppUIExecutor
        .onUiThread(ModalityState.any())
        .expireWith(myProject)
        .submit(() -> StreamEx.of(getWindows()).flatArray(EditorWindow::getEditors).toList())
        .onSuccess(allEditors -> ReadAction
          .nonBlocking(() -> calcEditorReplacements(allEditors))
          .inSmartMode(myProject)
          .finishOnUiThread(ModalityState.defaultModalityState(), this::replaceEditors)
          .coalesceBy(this)
          .submit(AppExecutorUtil.getAppExecutorService()));
    }

    private Map<EditorWithProviderComposite, Pair<VirtualFile, Integer>> calcEditorReplacements(List<EditorWithProviderComposite> allEditors) {
      List<EditorFileSwapper> swappers = EditorFileSwapper.EP_NAME.getExtensionList();
      return StreamEx.of(allEditors).mapToEntry(editor -> {
        if (editor.getFile().isValid()) {
          for (EditorFileSwapper each : swappers) {
            Pair<VirtualFile, Integer> fileAndOffset = each.getFileToSwapTo(myProject, editor);
            if (fileAndOffset != null) return fileAndOffset;
          }
        }
        return null;
      }).nonNullValues().toMap();
    }

    private void replaceEditors(Map<EditorWithProviderComposite, Pair<VirtualFile, Integer>> replacements) {
      if (replacements.isEmpty()) return;

      for (EditorWindow eachWindow : getWindows()) {
        EditorWithProviderComposite selected = eachWindow.getSelectedEditor();
        EditorWithProviderComposite[] editors = eachWindow.getEditors();
        for (int i = 0; i < editors.length; i++) {
          EditorWithProviderComposite editor = editors[i];
          VirtualFile file = editor.getFile();
          if (!file.isValid()) continue;

          Pair<VirtualFile, Integer> newFilePair = replacements.get(editor);
          if (newFilePair == null) continue;

          VirtualFile newFile = newFilePair.first;
          if (newFile == null) continue;

          // already open
          if (eachWindow.findFileIndex(newFile) != -1) continue;

          try {
            newFile.putUserData(EditorWindow.INITIAL_INDEX_KEY, i);
            Pair<FileEditor[], FileEditorProvider[]> pair = openFileImpl2(eachWindow, newFile, editor == selected);

            if (newFilePair.second != null) {
              TextEditorImpl openedEditor = EditorFileSwapper.findSinglePsiAwareEditor(pair.first);
              if (openedEditor != null) {
                openedEditor.getEditor().getCaretModel().moveToOffset(newFilePair.second);
                openedEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
              }
            }
          }
          finally {
            newFile.putUserData(EditorWindow.INITIAL_INDEX_KEY, null);
          }
          closeFile(file, eachWindow);
        }
      }
    }
  }

  /**
   * Gets notifications from UISetting component to track changes of RECENT_FILES_LIMIT
   * and EDITOR_TAB_LIMIT, etc values.
   */
  private final class MyUISettingsListener implements UISettingsListener {
    @Override
    public void uiSettingsChanged(@NotNull UISettings uiSettings) {
      assertDispatchThread();
      mySplitters.revalidate();
      for (EditorsSplitters each : getAllSplitters()) {
        each.setTabsPlacement(uiSettings.getEditorTabPlacement());
        each.trimToSize();

        if (JBTabsImpl.NEW_TABS) {
          TabsLayoutInfo tabsLayoutInfo = TabsLayoutSettingsManager.getInstance().getSelectedTabsLayoutInfo();
          each.updateTabsLayout(tabsLayoutInfo);
        }
        else {
          // Tab layout policy
          if (uiSettings.getScrollTabLayoutInEditor()) {
            each.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
          }
          else {
            each.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
          }
        }
      }

      // "Mark modified files with asterisk"
      VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        VirtualFile file = openFiles[i];
        updateFileIcon(file);
        updateFileName(file);
        updateFileBackgroundColor(file);
      }

      // "Show full paths in window header"
      updateFrameTitle();
    }
  }

  @Override
  public void closeAllFiles() {
    runBulkTabChange(getSplitters(), splitters -> {
      for (VirtualFile openFile : splitters.getOpenFileList()) {
        closeFile(openFile);
      }
    });
  }

  @Override
  public VirtualFile @NotNull [] getSiblings(@NotNull VirtualFile file) {
    return getOpenFiles();
  }

  void queueUpdateFile(@NotNull VirtualFile file) {
    myQueue.queue(new Update(file) {
      @Override
      public void run() {
        if (isFileOpen(file)) {
          updateFileIcon(file);
          updateFileColor(file);
          updateFileBackgroundColor(file);
        }

      }
    });
  }

  @Override
  public EditorsSplitters getSplittersFor(Component c) {
    EditorsSplitters splitters = null;
    DockContainer dockContainer = myDockManager.getContainerFor(c);
    if (dockContainer instanceof DockableEditorTabbedContainer) {
      splitters = ((DockableEditorTabbedContainer)dockContainer).getSplitters();
    }

    if (splitters == null) {
      splitters = getMainSplitters();
    }

    return splitters;
  }

  public @NotNull List<Pair<VirtualFile, EditorWindow>> getSelectionHistory() {
    List<Pair<VirtualFile, EditorWindow>> copy = new ArrayList<>();
    for (Pair<VirtualFile, EditorWindow> pair : mySelectionHistory) {
      if (pair.second.getFiles().length == 0) {
        EditorWindow[] windows = pair.second.getOwner().getWindows();
        if (windows.length > 0 && windows[0] != null && windows[0].getFiles().length > 0) {
          Pair<VirtualFile, EditorWindow> p = Pair.create(pair.first, windows[0]);
          if (!copy.contains(p)) {
            copy.add(p);
          }
        }
      } else {
        if (!copy.contains(pair)) {
          copy.add(pair);
        }
      }
    }
    mySelectionHistory.clear();
    mySelectionHistory.addAll(copy);
    return mySelectionHistory;
  }

  public void addSelectionRecord(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    Pair<VirtualFile, EditorWindow> record = Pair.create(file, window);
    mySelectionHistory.remove(record);
    mySelectionHistory.add(0, record);
  }

  void removeSelectionRecord(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    mySelectionHistory.remove(Pair.create(file, window));
    updateFileName(file);
  }

  @Override
  public @NotNull ActionCallback getReady(@NotNull Object requestor) {
    return myBusyObject.getReady(requestor);
  }
}
