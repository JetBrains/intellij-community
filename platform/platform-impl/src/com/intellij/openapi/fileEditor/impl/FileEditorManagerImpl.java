// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.MaximizeEditorInSplitAction;
import com.intellij.ide.actions.SplitAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LangBundle;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
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
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.pom.Navigatable;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.impl.DockManagerImpl;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.impl.MessageListenerList;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;
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

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_OPEN_IN_NEW_WINDOW;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT;

/**
 * @author Eugene Belyaev
 */
@State(name = "FileEditorManager", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)
})
public abstract class FileEditorManagerImpl extends FileEditorManagerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(FileEditorManagerImpl.class);
  protected static final Key<Boolean> DUMB_AWARE = Key.create("DUMB_AWARE");
  public static final Key<Boolean> NOTHING_WAS_OPENED_ON_START = Key.create("NOTHING_WAS_OPENED_ON_START");

  public static final Key<Boolean> CLOSING_TO_REOPEN = Key.create("CLOSING_TO_REOPEN");
  /**
   * Works on VirtualFile objects, and allows to disable the Preview Tab functionality for certain files.
   * If a virtual file has this key set to TRUE, the corresponding editor will always be opened in a regular tab.
   */
  public static final Key<Boolean> FORBID_PREVIEW_TAB = Key.create("FORBID_PREVIEW_TAB");
  public static final Key<Boolean> OPEN_IN_PREVIEW_TAB = Key.create("OPEN_IN_PREVIEW_TAB");
  /**
   * Works on FileEditor objects, allows to force opening other editor tabs in the main window.
   * If the currently selected file editor has this key set to TRUE, new editors will be opened in the main splitters.
   */
  public static final Key<Boolean> SINGLETON_EDITOR_IN_WINDOW = Key.create("OPEN_OTHER_TABS_IN_MAIN_WINDOW");
  public static final String FILE_EDITOR_MANAGER = "FileEditorManager";
  public static final String EDITOR_OPEN_INACTIVE_SPLITTER = "editor.open.inactive.splitter";

  public enum OpenMode {
    NEW_WINDOW,
    RIGHT_SPLIT,
    DEFAULT
  }

  private volatile @Nullable EditorsSplitters mySplitters;
  private final Project myProject;
  private final List<Pair<VirtualFile, EditorWindow>> mySelectionHistory = new ArrayList<>();
  private Reference<EditorComposite> myLastSelectedComposite = new WeakReference<>(null);

  private final MergingUpdateQueue queue = new MergingUpdateQueue("FileEditorManagerUpdateQueue", 50, true,
                                                                  MergingUpdateQueue.ANY_COMPONENT, this);

  private SoftReference<VirtualFile> fileToUpdateTitle;
  private final SingleAlarm updateFileTitleAlarm = new SingleAlarm(() -> {
    VirtualFile file = SoftReference.deref(fileToUpdateTitle);
    if (file == null || !file.isValid()) {
      return;
    }
    fileToUpdateTitle = null;
    for (EditorsSplitters each : getAllSplitters()) {
      each.updateFileName(file);
    }
  }, 50, this, Alarm.ThreadToUse.SWING_THREAD, ModalityState.NON_MODAL);

  private final BusyObject.Impl.Simple myBusyObject = new BusyObject.Impl.Simple();

  /**
   * Removes invalid myEditor and updates "modified" status.
   */
  private final PropertyChangeListener myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();
  private final DockManager myDockManager;
  private DockableEditorContainerFactory myContentFactory;
  private static final AtomicInteger ourOpenFilesSetModificationCount = new AtomicInteger();

  static final ModificationTracker OPEN_FILE_SET_MODIFICATION_COUNT = ourOpenFilesSetModificationCount::get;
  private final List<EditorComposite> myOpenedComposites = new CopyOnWriteArrayList<>();

  private final MessageListenerList<FileEditorManagerListener> myListenerList;
  private boolean myDisposed = false;

  public FileEditorManagerImpl(@NotNull Project project) {
    myProject = project;
    myDockManager = DockManager.getInstance(myProject);
    myListenerList = new MessageListenerList<>(myProject.getMessageBus(), FileEditorManagerListener.FILE_EDITOR_MANAGER);

    queue.setTrackUiActivity(true);

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
      public void projectClosing(@NotNull Project project) {
        if (project == myProject) {
          // Dispose created editors. We do not use closeEditor method because
          // it fires event and changes history.
          closeAllFiles();
        }
      }
    });

    closeFilesOnFileEditorRemoval();
    EditorFactory editorFactory = EditorFactory.getInstance();
    for (Editor editor : editorFactory.getAllEditors()) {
      registerEditor(editor);
    }
    editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        registerEditor(event.getEditor());
      }
    }, myProject);
  }

  private void registerEditor(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null || project.isDisposed() || myDisposed) {
      return;
    }
    if (editor instanceof EditorEx) {
      ((EditorEx)editor).addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(@NotNull Editor editor1) {
          if (!Registry.is("editor.maximize.on.focus.gained.if.collapsed.in.split")) return;
          Component comp = editor1.getComponent();
          while (comp != getMainSplitters() && comp != null) {
            Component parent = comp.getParent();
            if (parent instanceof Splitter) {
              Splitter splitter = (Splitter)parent;
              if ((splitter.getFirstComponent() == comp &&
                   (splitter.getProportion() == splitter.getMinProportion(true) ||
                    splitter.getProportion() == splitter.getMinimumProportion())) ||
                  (splitter.getProportion() == splitter.getMinProportion(false) ||
                   splitter.getProportion() == splitter.getMaximumProportion())) {
                Set<kotlin.Pair<Splitter, Boolean>> pairs = MaximizeEditorInSplitAction.Companion.getSplittersToMaximize(project, editor1.getComponent());
                for (kotlin.Pair<Splitter, Boolean> pair : pairs) {
                  Splitter s = pair.getFirst();
                  s.setProportion(pair.getSecond() ? s.getMaximumProportion() : s.getMinimumProportion());
                }
                break;
              }
            }
            comp = parent;
          }
        }
      }, this);
    }
  }

  private void closeFilesOnFileEditorRemoval() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull FileEditorProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        for (EditorComposite editor : myOpenedComposites) {
          for (FileEditorProvider provider : editor.getAllProviders()) {
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
    fileToUpdateTitle = null;
    myDisposed = true;
  }

  private void dumbModeFinished(@NotNull Project project) {
    VirtualFile[] files = getOpenFiles();
    for (VirtualFile file : files) {
      List<EditorComposite> composites = getAllComposites(file);
      List<FileEditorProvider> existingProviders = ContainerUtil.flatMap(composites, EditorComposite::getAllProviders);
      Set<String> existingIds = ContainerUtil.map2Set(existingProviders, FileEditorProvider::getEditorTypeId);

      List<FileEditorProvider> newProviders = FileEditorProviderManager.getInstance().getProviderList(project, file);
      List<FileEditorProvider> toOpen = ContainerUtil.filter(newProviders, it -> !existingIds.contains(it.getEditorTypeId()));

      // need to open additional non dumb-aware editors
      for (EditorComposite composite : composites) {
        for (FileEditorProvider provider : toOpen) {
          FileEditor editor = provider.createEditor(myProject, file);
          composite.addEditor(editor, provider);
        }
      }
      updateFileBackgroundColor(file);
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
      DockContainer container = myDockManager.getContainerFor(focusOwner, DockableEditorTabbedContainer.class::isInstance);
      if (container instanceof DockableEditorTabbedContainer) {
        result.setResult(((DockableEditorTabbedContainer)container).getSplitters());
      }
      else {
        result.setResult(getMainSplitters());
      }
    }, ModalityState.defaultModalityState());
    return result;
  }

  private @NotNull EditorsSplitters getActiveSplittersSync() {
    assertDispatchThread();
    if (Registry.is("ide.navigate.to.recently.focused.editor", false)) {
      List<EditorsSplitters> splitters = new ArrayList<>(getAllSplitters());
      if (!splitters.isEmpty()) {
        splitters.sort((o1, o2) -> Long.compare(o2.getLastFocusGainedTime(), o1.getLastFocusGainedTime()));
        return splitters.get(0);
      }
    }
    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component focusOwner = fm.getFocusOwner();
    if (focusOwner == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }
    if (focusOwner == null) {
      focusOwner = fm.getLastFocusedFor(fm.getLastFocusedIdeWindow());
    }

    DockContainer container = myDockManager.getContainerFor(focusOwner, DockableEditorTabbedContainer.class::isInstance);
    if (container == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      container = myDockManager.getContainerFor(focusOwner, DockableEditorTabbedContainer.class::isInstance);
    }

    if (container instanceof DockableEditorTabbedContainer) {
      return ((DockableEditorTabbedContainer)container).getSplitters();
    }
    return getMainSplitters();
  }

  private final Object myInitLock = new Object();

  private @NotNull EditorsSplitters initUI() {
    EditorsSplitters result = mySplitters;
    if (result == null) {
      synchronized (myInitLock) {
        result = mySplitters;
        if (result == null) {
          result = new EditorsSplitters(this);
          DockableEditorTabbedContainer dockable = new DockableEditorTabbedContainer(myProject, result, false);
          DockManager.getInstance(myProject).register(dockable, this);
          Disposer.register(this, dockable);

          // prepare for toolwindow manager
          result.setFocusable(false);

          mySplitters = result;
        }
      }
    }
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    assertReadAccess();
    EditorWindow window = getSplitters().getCurrentWindow();
    if (window != null) {
      EditorComposite composite = window.getSelectedComposite();
      if (composite != null) {
        return composite.getPreferredFocusedComponent();
      }
    }
    return null;
  }

  //-------------------------------------------------------

  /**
   * @return color of the {@code file} which corresponds to the
   *         file's status
   */
  public @NotNull Color getFileColor(@NotNull VirtualFile file) {
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    Color statusColor = fileStatusManager != null ? fileStatusManager.getStatus(file).getColor() : UIUtil.getLabelForeground();
    if (statusColor == null) statusColor = UIUtil.getLabelForeground();
    return statusColor;
  }

  public boolean isProblem(@NotNull VirtualFile file) {
    return false;
  }

  public @NotNull @NlsContexts.Tooltip String getFileTooltipText(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    String prefix = "";
    EditorComposite composite = window.getComposite(file);
    if (composite != null && composite.isPreview()) {
      prefix = LangBundle.message("preview.editor.tab.tooltip.text") + " ";
    }
    List<EditorTabTitleProvider> availableProviders = DumbService.getDumbAwareExtensions(myProject, EditorTabTitleProvider.EP_NAME);
    for (EditorTabTitleProvider provider : availableProviders) {
      String text = provider.getEditorTabTooltipText(myProject, file);
      if (text != null) {
        return prefix + text;
      }
    }
    return prefix + FileUtil.getLocationRelativeToUserHome(file.getPresentableUrl());
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
  @Override
  public void updateFileColor(@NotNull VirtualFile file) {
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      each.updateFileColor(file);
    }
  }

  private void updateFileBackgroundColor(@NotNull VirtualFile file) {
    if (ExperimentalUI.isNewUI()) return;
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
    updateFileIcon(file, false);
  }

  /**
   * Reset the preview tab flag if an internal document change is made.
   */
  private void resetPreviewFlag(@NotNull VirtualFile file) {
    if (!FileDocumentManager.getInstance().isFileModified(file)) {
      return;
    }
    for (EditorsSplitters splitter : getAllSplitters()) {
      splitter.getAllComposites(file).stream()
        .filter(EditorComposite::isPreview)
        .forEach(c -> c.setPreview(false));
      splitter.updateFileColor(file);
    }
  }

  /**
   * Updates tab icon for the specified {@code file}. The {@code file}
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  protected void updateFileIcon(@NotNull VirtualFile file, boolean immediately) {
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      if (immediately) {
        each.updateFileIconImmediately(file, IconUtil.computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, myProject));
      }
      else {
        each.updateFileIcon(file);
      }
    }
  }

  /**
   * Updates tab title and tab tool tip for the specified {@code file}
   */
  void updateFileName(@Nullable VirtualFile file) {
    // Queue here is to prevent title flickering when tab is being closed and two events arriving: with component==null and component==next focused tab
    // only the last event makes sense to handle
    updateFileTitleAlarm.cancelAndRequest();
    fileToUpdateTitle = new SoftReference<>(file);
  }

  private void updateFrameTitle() {
    getActiveSplittersAsync().onSuccess(splitters -> splitters.updateFileName(null));
  }

  @SuppressWarnings("removal")
  @Override
  public VirtualFile getFile(@NotNull FileEditor editor) {
    EditorComposite editorComposite = getComposite(editor);
    VirtualFile tabFile = editorComposite == null ? null : editorComposite.getFile();
    VirtualFile editorFile = editor.getFile();
    if (!Objects.equals(editorFile, tabFile)) {
      if (editorFile == null) {
        LOG.warn(editor.getClass().getName() + ".getFile() shall not return null");
      }
      else if (tabFile == null) {
        //todo DaemonCodeAnalyzerImpl#getSelectedEditors calls it for any Editor
        //LOG.warn(editor.getClass().getName() + ".getFile() shall be used, fileEditor is not opened in a tab.");
      }
      else {
        LOG.warn("fileEditor.getFile=" + editorFile + " != fileEditorManager.getFile=" + tabFile +
                 ", fileEditor.class=" + editor.getClass().getName());
      }
    }
    return tabFile;
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
    return currentWindow != null && currentWindow.getSelectedComposite() != null;
  }

  @Override
  public VirtualFile getCurrentFile() {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager == null) {
        return null;
      }
      return clientManager.getSelectedFile();
    }
    // if mySplitters is null, it means that not yet initialized
    return mySplitters == null ? null : getActiveSplittersSync().getCurrentFile();
  }

  @Override
  public @NotNull Promise<EditorWindow> getActiveWindow() {
    return getActiveSplittersAsync()
      .then(EditorsSplitters::getCurrentWindow);
  }

  @Override
  public EditorWindow getCurrentWindow() {
    if (!ClientId.isCurrentlyUnderLocalId()) return null;
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      LOG.warn("Requesting getCurrentWindow() on BGT, returning null", new Throwable());
      return null;
    }
    return getActiveSplittersSync().getCurrentWindow();
  }

  @Override
  public void setCurrentWindow(EditorWindow window) {
    if (ClientId.isCurrentlyUnderLocalId()) {
      getActiveSplittersSync().setCurrentWindow(window, true);
    }
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
    if (!closeAllCopies) {
      if (ClientId.isCurrentlyUnderLocalId()) {
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          ourOpenFilesSetModificationCount.incrementAndGet();
          runChange(splitters -> splitters.closeFile(file, moveFocus), getActiveSplittersSync());
        }, "", null);
      } else {
        ClientFileEditorManager clientManager = getClientFileEditorManager();
        if (clientManager != null) {
          clientManager.closeFile(file, false);
        }
      }
    }
    else {
      try (AccessToken ignored = ClientId.withClientId(ClientId.getLocalId())) {
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          ourOpenFilesSetModificationCount.incrementAndGet();
          runChange(splitters -> splitters.closeFile(file, moveFocus), null);
        }, "", null);
      }
      for (ClientFileEditorManager manager: getAllClientFileEditorManagers()) {
        manager.closeFile(file, true);
      }
    }
  }

  private List<ClientFileEditorManager> getAllClientFileEditorManagers() {
    return myProject.getServices(ClientFileEditorManager.class, false);
  }

  @Override
  public boolean isFileOpenWithRemotes(@NotNull VirtualFile file) {
      if (isFileOpen(file)) {
        return true;
      }
      for (ClientFileEditorManager m: getAllClientFileEditorManagers()) {
        if (m.isFileOpen(file)) {
          return true;
        }
      }
      return false;
  }

  //-------------------------------------- Open File ----------------------------------------

  @Override
  public final @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                 boolean focusEditor,
                                                                                 boolean searchForSplitter) {
    FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
      .withRequestFocus(focusEditor)
      .withReuseOpen(searchForSplitter);
    return openFileWithProviders(file, null, openOptions);
  }

  @Override
  public final @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                 boolean focusEditor,
                                                                                 @NotNull EditorWindow window) {
    return openFileWithProviders(file, window, new FileEditorOpenOptions().withRequestFocus(focusEditor));
  }

  @Override
  public final @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                 @Nullable EditorWindow window,
                                                                                 @NotNull FileEditorOpenOptions options) {
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertDispatchThread();
    if (window != null && window.isDisposed()) {
      window = null;
    }

    if (window == null) {
      OpenMode mode = getOpenMode(IdeEventQueue.getInstance().getTrueCurrentEvent());
      if (mode == OpenMode.NEW_WINDOW) {
        return openFileInNewWindow(file);
      }
      if (mode == OpenMode.RIGHT_SPLIT) {
        Pair<FileEditor[], FileEditorProvider[]> pair = openInRightSplit(file);
        if (pair != null) return pair;
      }
    }

    EditorWindow windowToOpenIn = window;
    if (windowToOpenIn == null && (options.getReuseOpen() || !AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER))) {
      windowToOpenIn = findWindowInAllSplitters(file);
    }
    if (windowToOpenIn == null) {
      windowToOpenIn = getOrCreateCurrentWindow(file);
    }
    return openFileImpl2(windowToOpenIn, file, options);
  }

  private @Nullable EditorWindow findWindowInAllSplitters(@NotNull VirtualFile file) {
    EditorWindow activeCurrentWindow = getActiveSplittersSync().getCurrentWindow();
    if (activeCurrentWindow != null && isFileOpenInWindow(file, activeCurrentWindow)) {
      return activeCurrentWindow;
    }
    for (EditorsSplitters splitters : getAllSplitters()) {
      EditorWindow[] windows = splitters.getWindows();
      for (EditorWindow window : windows) {
        if (isFileOpenInWindow(file, window)) {
          if (AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER)) {
            return window;
          }

          // return a window from here so that we don't look for it again in getOrCreateCurrentWindow
          return activeCurrentWindow;
        }
      }
    }
    return null;
  }

  private static boolean isFileOpenInWindow(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    boolean shouldFileBeSelected = UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE;
    return shouldFileBeSelected ? file.equals(window.getSelectedFile())
                                : window.isFileOpen(file);
  }

  private @NotNull EditorWindow getOrCreateCurrentWindow(@NotNull VirtualFile file) {
    boolean useMainWindow = UISettings.getInstance().getOpenTabsInMainWindow() ||
                            SINGLETON_EDITOR_IN_WINDOW.get(getSelectedEditor(), false);
    EditorsSplitters splitters = useMainWindow ? getMainSplitters() : getSplitters();

    EditorWindow currentWindow = splitters.getCurrentWindow();
    return currentWindow != null && UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE
           ? currentWindow
           : splitters.getOrCreateCurrentWindow(file);
  }

  public final Pair<FileEditor[], FileEditorProvider[]> openFileInNewWindow(@NotNull VirtualFile file) {
    if (forbidSplitFor(file)) {
      closeFile(file);
    }
    return ((DockManagerImpl)DockManager.getInstance(getProject())).createNewDockContainerFor(file, this);
  }

  private @Nullable Pair<FileEditor[], FileEditorProvider[]> openInRightSplit(@NotNull VirtualFile file) {
    EditorsSplitters active = getSplitters();
    EditorWindow window = active.getCurrentWindow();
    if (window != null) {
      if (window.inSplitter() &&
          file.equals(window.getSelectedFile()) &&
          file.equals(ArrayUtil.getLastElement(window.getFiles()))) {
        //already in right splitter
        return null;
      }

      EditorWindow split = active.openInRightSplit(file);
      if (split != null) {
        Ref<Pair<FileEditor[], FileEditorProvider[]>> ref = Ref.create();
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          List<EditorComposite> composites = split.getAllComposites();
          List<FileEditorWithProvider> editorsWithProviders = ContainerUtil.flatMap(composites, EditorComposite::getAllEditorsWithProviders);
          FileEditor[] editors = ContainerUtil.map2Array(editorsWithProviders, FileEditor.class, FileEditorWithProvider::getFileEditor);
          FileEditorProvider[] providers = ContainerUtil.map2Array(editorsWithProviders, FileEditorProvider.class,
                                                                   FileEditorWithProvider::getProvider);
          ref.set(Pair.create(editors, providers));
        }, "", null);

        return ref.get();
      }
    }
    return null;
  }

  public static @NotNull OpenMode getOpenMode(@NotNull AWTEvent event) {
    if (event instanceof MouseEvent) {
      boolean isMouseClick = event.getID() == MouseEvent.MOUSE_CLICKED ||
                  event.getID() == MouseEvent.MOUSE_PRESSED ||
                  event.getID() == MouseEvent.MOUSE_RELEASED;
      int modifiers = ((MouseEvent)event).getModifiersEx();
      if (modifiers == InputEvent.SHIFT_DOWN_MASK && isMouseClick) {
        return OpenMode.NEW_WINDOW;
      }
    }

    if (event instanceof KeyEvent) {
      KeyEvent ke = (KeyEvent)event;
      KeymapManager keymapManager = KeymapManager.getInstance();
      if (keymapManager != null) {
        Keymap keymap = keymapManager.getActiveKeymap();
        String[] ids = keymap.getActionIds(KeyStroke.getKeyStroke(ke.getKeyCode(), ke.getModifiers()));
        List<String> strings = Arrays.asList(ids);
        if (strings.contains(ACTION_OPEN_IN_NEW_WINDOW)) return OpenMode.NEW_WINDOW;
        if (strings.contains(ACTION_OPEN_IN_RIGHT_SPLIT)) return OpenMode.RIGHT_SPLIT;
      }
    }

    return OpenMode.DEFAULT;
  }

  public static boolean forbidSplitFor(@NotNull VirtualFile file) {
    return Boolean.TRUE.equals(file.getUserData(SplitAction.FORBID_TAB_SPLIT));
  }

  public final @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(@NotNull EditorWindow window,
                                                                         @NotNull VirtualFile file,
                                                                         boolean focusEditor) {
    return openFileImpl2(window, file, new FileEditorOpenOptions().withRequestFocus(focusEditor));
  }

  public @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(@NotNull EditorWindow window,
                                                                         @NotNull VirtualFile file,
                                                                         @NotNull FileEditorOpenOptions options) {
    if (forbidSplitFor(file) && !window.isFileOpen(file)) {
      closeFile(file);
    }

    Ref<Pair<FileEditor[], FileEditorProvider[]>> result = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      Pair<FileEditor[], FileEditorProvider[]> editorsProvidersPair = openFileImpl4(window, file, null, options);
      result.set(editorsProvidersPair);
    }, "", null);
    return result.get();
  }

  /**
   * @param file    to be opened. Unlike openFile method, file can be
   *                invalid. For example, all file where invalidate, and they are being
   *                removed one by one. If we have removed one invalid file, then another
   *                invalid file become selected. That's why we do not require that
   *                passed file is valid.
   * @param entry   map between FileEditorProvider and FileEditorState. If this parameter
   */
  final @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(@NotNull EditorWindow window,
                                                                  @NotNull VirtualFile file,
                                                                  boolean focusEditor,
                                                                  @Nullable HistoryEntry entry) {
    return openFileImpl4(window, file, entry, new FileEditorOpenOptions().withRequestFocus(focusEditor));
  }

  protected final @Nullable ClientFileEditorManager getClientFileEditorManager() {
    ClientId clientId = ClientId.getCurrent();
    LOG.assertTrue(!ClientId.isLocal(clientId), "Trying to get ClientFileEditorManager for local ClientId");
    ClientProjectSession session = ClientSessionsManager.getProjectSession(myProject, clientId);
    return session == null ? null : session.getService(ClientFileEditorManager.class);
  }

  /**
   * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
   */
  protected @NotNull Pair<FileEditor @NotNull [], FileEditorProvider @NotNull []> openFileImpl4(@NotNull EditorWindow window,
                                                                                                @NotNull VirtualFile _file,
                                                                                                @Nullable HistoryEntry entry,
                                                                                                @NotNull FileEditorOpenOptions options) {
    assert ApplicationManager.getApplication().isDispatchThread() ||
           !ApplicationManager.getApplication().isReadAccessAllowed() : "must not attempt opening files under read action";

    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager == null) {
        return Pair.createNonNull(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY);
      }

      List<FileEditorWithProvider> result = clientManager.openFile(_file, false);
      FileEditor[] fileEditors = result.stream().map(FileEditorWithProvider::getFileEditor).toArray(FileEditor[]::new);
      FileEditorProvider[] providers = result.stream().map(FileEditorWithProvider::getProvider).toArray(FileEditorProvider[]::new);
      return Pair.createNonNull(fileEditors, providers);
    }

    VirtualFile file = getOriginalFile(_file);
    Ref<EditorComposite> compositeRef = new Ref<>();

    if (!options.isReopeningOnStartup()) {
      EdtInvocationManager.invokeAndWaitIfNeeded(() -> compositeRef.set(window.getComposite(file)));
    }

    List<FileEditorProvider> newProviders;
    AsyncFileEditorProvider.Builder[] builders;
    if (compositeRef.isNull()) {
      if (!canOpenFile(file)) return EditorComposite.retrofit(null);

      // File is not opened yet. In this case we have to create editors
      // and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProviderList(myProject, file);
      builders = new AsyncFileEditorProvider.Builder[newProviders.size()];
      for (int i = 0; i < newProviders.size(); i++) {
        try {
          FileEditorProvider provider = newProviders.get(i);
          LOG.assertTrue(provider != null, "Provider for file "+file+" is null. All providers: "+newProviders);
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
      if (myProject.isDisposed() || !file.isValid()) return;

      runBulkTabChange(window.getOwner(), splitters -> {
        EditorComposite composite = openFileImpl4Edt(window, file, entry, options, newProviders, builders);
        compositeRef.set(composite);
      });
    });

    return EditorComposite.retrofit(compositeRef.get());
  }

  protected final @Nullable EditorComposite openFileImpl4Edt(@NotNull EditorWindow window,
                                                             @NotNull VirtualFile file,
                                                             @Nullable HistoryEntry entry,
                                                             @NotNull FileEditorOpenOptions options,
                                                             @Nullable List<FileEditorProvider> newProviders,
                                                             AsyncFileEditorProvider.Builder @Nullable [] builders) {
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
    LOG.assertTrue(file.isValid(), "Invalid file: " + file);

    if (options.getRequestFocus()) {
      Project activeProject = ProjectUtil.getActiveProject();
      if (activeProject != null && !activeProject.equals(myProject)) {
        // allow focus switching only within a project
        options = options.clone().withRequestFocus(false);
      }
    }
    if (entry != null && entry.isPreview()) {
      options = options.clone().withUsePreviewTab();
    }

    EditorComposite composite = window.getComposite(file);
    boolean newEditor = composite == null;
    if (newEditor) {
      LOG.assertTrue(newProviders != null && builders != null);
      composite = createComposite(file, newProviders, builders);
      if (composite == null) return null;

      getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileOpened(this, file);
      myOpenedComposites.add(composite);
    }

    List<FileEditorWithProvider> editorsWithProviders = composite.getAllEditorsWithProviders();

    window.setComposite(composite, options);

    for (FileEditorWithProvider editorWithProvider : editorsWithProviders) {
      restoreEditorState(file, editorWithProvider, entry, newEditor, options.isExactState());
    }
    // Restore selected editor
    FileEditorProvider provider = entry == null
                                  ? FileEditorProviderManagerImpl.Companion.getInstanceImpl().getSelectedFileEditorProvider(composite)
                                  : entry.getSelectedProvider();

    if (provider != null) {
      composite.setSelectedEditor(provider.getEditorTypeId());
    }

    // Notify editors about selection changes
    EditorsSplitters splitters = window.getOwner();
    splitters.setCurrentWindow(window, options.getRequestFocus());
    splitters.afterFileOpen(file);
    addSelectionRecord(file, window);

    FileEditor selectedEditor = composite.getSelectedEditor();
    selectedEditor.selectNotify();

    // transfer focus into editor
    if (options.getRequestFocus() && !ApplicationManager.getApplication().isUnitTestMode()) {
      EditorComposite finalComposite = composite;
      Runnable focusRunnable = () -> {
        if (splitters.getCurrentWindow() != window || window.getSelectedComposite() != finalComposite) {
          // While the editor was loading asynchronously, the user switched to another editor.
          // Don't steal focus.
          return;
        }

        Window windowAncestor = SwingUtilities.getWindowAncestor(window.panel);
        if (windowAncestor != null && windowAncestor.equals(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow())) {
          JComponent component = finalComposite.getPreferredFocusedComponent();
          if (component != null) {
            component.requestFocus();
          }
        }
      };
      if (selectedEditor instanceof TextEditor) {
        runWhenLoaded(((TextEditor)selectedEditor).getEditor(), focusRunnable);
      }
      else {
        focusRunnable.run();
      }
      IdeFocusManager.getInstance(myProject).toFront(splitters);
    }

    if (newEditor) {
      ourOpenFilesSetModificationCount.incrementAndGet();
    }

    //[jeka] this is a hack to support back-forward navigation
    // previously here was incorrect call to fireSelectionChanged() with a side-effect
    IdeDocumentHistory ideDocumentHistory = IdeDocumentHistory.getInstance(myProject);
    ((IdeDocumentHistoryImpl)ideDocumentHistory).onSelectionChanged();

    // Update frame and tab title
    updateFileName(file);

    // Make back/forward work
    ideDocumentHistory.includeCurrentCommandAsNavigation();

    if (options.getPin() != null) {
      window.setFilePinned(file, options.getPin());
    }

    if (newEditor) {
      myProject.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
        .fileOpenedSync(this, file, editorsWithProviders);

      notifyPublisher(() -> {
        if (isFileOpen(file)) {
          myProject.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(this, file);
        }
      });
    }

    return composite;
  }

  protected @Nullable EditorComposite createComposite(@NotNull VirtualFile file,
                                                      @NotNull List<FileEditorProvider> providers,
                                                      AsyncFileEditorProvider.Builder @NotNull [] builders) {
    List<FileEditorWithProvider> editorsWithProviders = new ArrayList<>(providers.size());
    for (int i = 0; i < providers.size(); i++) {
      try {
        FileEditorProvider provider = providers.get(i);
        if (provider == null) {
          continue;
        }

        FileEditor editor = builders[i] == null ? provider.createEditor(myProject, file) : builders[i].build();
        LOG.assertTrue(editor.isValid(), "Invalid editor created by provider " + provider.getClass().getName());
        editorsWithProviders.add(new FileEditorWithProvider(editor, provider));
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception | AssertionError e) {
        LOG.error(e);
      }
    }

    return createComposite(file, editorsWithProviders);
  }

  protected @Nullable EditorComposite createComposite(@NotNull VirtualFile file,
                                                      @NotNull List<FileEditorWithProvider> editorsWithProviders) {
    for (FileEditorWithProvider editorWithProvider : editorsWithProviders) {
      FileEditor editor = editorWithProvider.getFileEditor();
      editor.addPropertyChangeListener(myEditorPropertyChangeListener);
      editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.getProvider()));
    }
    return createCompositeInstance(file, editorsWithProviders);
  }

  @Contract("_, _ -> new")
  protected @Nullable EditorComposite createCompositeInstance(@NotNull VirtualFile file,
                                                              @NotNull List<FileEditorWithProvider> editorsWithProviders) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      return clientManager == null ? null : clientManager.createComposite(file, editorsWithProviders);
    }
    // the only place this class in created, won't be needed when we get rid of EditorWithProviderComposite usages
    //noinspection deprecation
    return new EditorWithProviderComposite(file, editorsWithProviders, this);
  }

  private void restoreEditorState(@NotNull VirtualFile file,
                                  @NotNull FileEditorWithProvider editorWithProvider,
                                  HistoryEntry entry,
                                  boolean newEditor,
                                  boolean exactState) {
    FileEditorState state = null;
    FileEditorProvider provider = editorWithProvider.getProvider();
    if (entry != null) {
      state = entry.getState(provider);
    }
    if (state == null && newEditor) {
      // We have to try to get state from the history only in case
      // if editor is not opened. Otherwise, history entry might have a state
      // out of sync with the current editor state.
      state = EditorHistoryManager.getInstance(myProject).getState(file, provider);
    }
    if (state != null) {
      FileEditor editor = editorWithProvider.getFileEditor();
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
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if(clientManager != null) {
        clientManager.setSelectedEditor(file, fileEditorProviderId);
      }
      return;
    }
    EditorComposite composite = getComposite(file);
    if (composite == null) return;

    composite.setSelectedEditor(fileEditorProviderId);
    // todo move to setSelectedEditor()?
    composite.getSelectedEditor().selectNotify();
  }

  @Nullable
  EditorComposite newEditorComposite(@NotNull VirtualFile file) {
    if (!canOpenFile(file)) return null;

    List<FileEditorProvider> providers = FileEditorProviderManager.getInstance().getProviderList(myProject, file);
    EditorComposite newComposite = createComposite(file, providers, new AsyncFileEditorProvider.Builder[providers.size()]);
    if (newComposite == null) {
      return null;
    }

    EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(myProject);
    for (FileEditorWithProvider editorWithProvider : newComposite.getAllEditorsWithProviders()) {
      FileEditor editor = editorWithProvider.getFileEditor();
      FileEditorProvider provider = editorWithProvider.getProvider();

      // Restore myEditor state
      FileEditorState state = editorHistoryManager.getState(file, provider);
      if (state != null) {
        editor.setState(state);
      }
    }
    return newComposite;
  }

  @Override
  public final @NotNull List<FileEditor> openFileEditor(@NotNull FileEditorNavigatable descriptor, boolean focusEditor) {
    return openEditorImpl(descriptor, focusEditor).first;
  }

  /**
   * @return the list of opened editors, and the one of them that was selected (if any)
   */
  private Pair<List<FileEditor>, FileEditor> openEditorImpl(@NotNull FileEditorNavigatable descriptor, boolean focusEditor) {
    assertDispatchThread();
    FileEditorNavigatable realDescriptor;
    if (descriptor instanceof OpenFileDescriptor && descriptor.getFile() instanceof VirtualFileWindow) {
      OpenFileDescriptor openFileDescriptor = (OpenFileDescriptor)descriptor;
      VirtualFileWindow delegate = (VirtualFileWindow)descriptor.getFile();
      int hostOffset = delegate.getDocumentWindow().injectedToHost(openFileDescriptor.getOffset());
      OpenFileDescriptor fixedDescriptor = new OpenFileDescriptor(openFileDescriptor.getProject(), delegate.getDelegate(), hostOffset);
      fixedDescriptor.setUseCurrentWindow(openFileDescriptor.isUseCurrentWindow());
      fixedDescriptor.setUsePreviewTab(openFileDescriptor.isUsePreviewTab());
      realDescriptor = fixedDescriptor;
    }
    else {
      realDescriptor = descriptor;
    }

    List<FileEditor> result = new SmartList<>();
    Ref<FileEditor> selectedEditor = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      VirtualFile file = realDescriptor.getFile();
      FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
        .withReuseOpen(!realDescriptor.isUseCurrentWindow())
        .withUsePreviewTab(realDescriptor.isUsePreviewTab())
        .withRequestFocus(focusEditor);
      FileEditor[] editors = openFileWithProviders(file, null, openOptions).getFirst();
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

  private boolean navigateAndSelectEditor(@NotNull NavigatableFileEditor editor, @NotNull Navigatable descriptor) {
    if (editor.canNavigateTo(descriptor)) {
      setSelectedEditor(editor);
      editor.navigateTo(descriptor);
      return true;
    }

    return false;
  }

  private void setSelectedEditor(@NotNull FileEditor editor) {
    EditorComposite composite = getComposite(editor);
    if (composite == null) return;
    composite.setSelectedEditor(editor);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public final @Nullable Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    Pair<List<FileEditor>, FileEditor> editorsWithSelected = openEditorImpl(descriptor, focusEditor);
    Collection<FileEditor> fileEditors = editorsWithSelected.first;
    FileEditor selectedEditor = editorsWithSelected.second;

    if (fileEditors.isEmpty()) return null;
    else if (fileEditors.size() == 1) {
      TextEditor editor = ObjectUtils.tryCast(ContainerUtil.getFirstItem(fileEditors), TextEditor.class);
      return editor != null ? editor.getEditor() : null;
    }

    List<TextEditor> textEditors = ContainerUtil.mapNotNull(fileEditors, e -> ObjectUtils.tryCast(e, TextEditor.class));
    if (textEditors.isEmpty()) return null;

    TextEditor target = selectedEditor instanceof TextEditor ? (TextEditor)selectedEditor : textEditors.get(0);
    if (textEditors.size() > 1) {
      EditorComposite composite = getComposite(target);
      assert composite != null;
      List<FileEditorWithProvider> editorsWithProviders = composite.getAllEditorsWithProviders();
      String textProviderId = TextEditorProvider.getInstance().getEditorTypeId();
      for (FileEditorWithProvider editorWithProvider : editorsWithProviders) {
        FileEditor editor = editorWithProvider.getFileEditor();
        if (editor instanceof TextEditor && editorWithProvider.getProvider().getEditorTypeId().equals(textProviderId)) {
          target = (TextEditor)editor;
          break;
        }
      }
    }
    setSelectedEditor(target);
    return target.getEditor();
  }

  @Override
  public FileEditor @NotNull [] getSelectedEditorWithRemotes() {
    List<FileEditor> result = new ArrayList<>();
    Collections.addAll(result, getSelectedEditors());
    for (ClientFileEditorManager m : getAllClientFileEditorManagers()) {
      result.addAll(m.getSelectedEditors());
    }
    return result.toArray(FileEditor.EMPTY_ARRAY);
  }

  @Override
  public Editor @NotNull [] getSelectedTextEditorWithRemotes() {
    List<Editor> result = new ArrayList<>();
    for(FileEditor e: getSelectedEditorWithRemotes()) {
      if (e instanceof TextEditor) {
        result.add(((TextEditor)e).getEditor());
      }
    }
    return result.toArray(Editor.EMPTY_ARRAY);
  }

  @Override
  public Editor getSelectedTextEditor() {
    return getSelectedTextEditor(false);
  }

  public Editor getSelectedTextEditor(boolean isLockFree) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager == null) {
        return null;
      }
      FileEditor selectedEditor = clientManager.getSelectedEditor();
      return selectedEditor instanceof TextEditor ? ((TextEditor)selectedEditor).getEditor() : null;
    }
    Editor editor = IntentionPreviewUtils.getPreviewEditor();
    if (editor != null) {
      return editor;
    }

    if (!isLockFree) {
      assertDispatchThread();
    }

    EditorWindow currentWindow = isLockFree ? getMainSplitters().getCurrentWindow() : getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      EditorComposite selectedEditor = currentWindow.getSelectedComposite();
      if (selectedEditor != null && selectedEditor.getSelectedEditor() instanceof TextEditor) {
        return ((TextEditor)selectedEditor.getSelectedEditor()).getEditor();
      }
    }

    return null;
  }

  @Override
  public boolean isFileOpen(@NotNull VirtualFile file) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager == null) return false;
      return clientManager.isFileOpen(file);
    }
    for (EditorComposite editor : myOpenedComposites) {
      if (editor.getFile().equals(file)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public VirtualFile @NotNull [] getOpenFiles() {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager == null) {
        return VirtualFile.EMPTY_ARRAY;
      }
      return clientManager.getAllFiles().toArray(VirtualFile.EMPTY_ARRAY);
    }
    Set<VirtualFile> files = new LinkedHashSet<>();
    for (EditorComposite composite : myOpenedComposites) {
      files.add(composite.getFile());
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public VirtualFile @NotNull [] getOpenFilesWithRemotes() {
    List<VirtualFile> result = new ArrayList<>();
    Collections.addAll(result, getOpenFiles());
    for (ClientFileEditorManager m : getAllClientFileEditorManagers()) {
      result.addAll(m.getAllFiles());
    }
    return result.toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public boolean hasOpenFiles() {
    return !myOpenedComposites.isEmpty();
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager == null) {
        return VirtualFile.EMPTY_ARRAY;
      }
      return clientManager.getSelectedFiles().toArray(VirtualFile.EMPTY_ARRAY);
    }

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
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      return clientManager == null ? FileEditor.EMPTY_ARRAY : clientManager.getSelectedEditors().toArray(FileEditor.EMPTY_ARRAY);
    }

    Set<FileEditor> selectedEditors = new SmartHashSet<>();
    for (EditorsSplitters splitters : getAllSplitters()) {
      splitters.addSelectedEditorsTo(selectedEditors);
    }
    return selectedEditors.toArray(FileEditor.EMPTY_ARRAY);
  }

  @Override
  public @NotNull EditorsSplitters getSplitters() {
    return ApplicationManager.getApplication().isDispatchThread() ? getActiveSplittersSync() : getMainSplitters();
  }

  @Override
  public @Nullable FileEditor getSelectedEditor() {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      return clientManager == null ? null : clientManager.getSelectedEditor();
    }

    EditorWindow window = getSplitters().getCurrentWindow();
    if (window != null) {
      EditorComposite selected = window.getSelectedComposite();
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
    EditorComposite composite = getComposite(file);
    return composite == null ? null : composite.getSelectedWithProvider();
  }

  @Override
  public @NotNull Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getComposite(file);
    return EditorComposite.retrofit(composite);
  }

  @Override
  public FileEditor @NotNull [] getEditors(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getEditorsWithProviders(file).getFirst();
  }

  @Override
  public FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file) {
    List<FileEditor> result = new ArrayList<>();
    // reuse getAllComposites(file)? Are there cases some composites are not accessible via splitters?
    for (EditorComposite composite : myOpenedComposites) {
      if (composite.getFile().equals(file)) {
        result.addAll(composite.getAllEditors());
      }
    }
    for (ClientFileEditorManager clientManager : getAllClientFileEditorManagers()) {
      result.addAll(clientManager.getEditors(file));
    }

    return result.toArray(FileEditor.EMPTY_ARRAY);
  }

  @Override
  public @Nullable EditorComposite getComposite(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      return clientManager == null ? null : clientManager.getComposite(file);
    }
    VirtualFile originalFile = getOriginalFile(file);

    EditorWindow editorWindow = getSplitters().getCurrentWindow();
    if (editorWindow != null) {
      EditorComposite composite = editorWindow.getComposite(file);
      if (composite != null) {
        return composite;
      }
    }
    for (EditorsSplitters each : getAllSplitters()) {
      EditorComposite composite = ContainerUtil.find(each.getAllComposites(originalFile), it -> it.getFile().equals(originalFile));
      if (composite != null) {
        return composite;
      }
    }

    return null;
  }

  public @NotNull List<EditorComposite> getAllComposites(@NotNull VirtualFile file) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      return clientManager == null ? new ArrayList<>() : clientManager.getAllComposites(file);
    }
    List<EditorComposite> result = new ArrayList<>();
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      result.addAll(each.getAllComposites(file));
    }
    return result;
  }

  @Override
  public FileEditor @NotNull [] getAllEditors() {
    List<FileEditor> result = new ArrayList<>();
    for (EditorComposite composite : myOpenedComposites) {
      result.addAll(composite.getAllEditors());
    }
    for (ClientFileEditorManager clientManager : getAllClientFileEditorManagers()) {
      result.addAll(clientManager.getAllEditors());
    }
    return result.toArray(FileEditor.EMPTY_ARRAY);
  }

  public @NotNull List<JComponent> getTopComponents(@NotNull FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getComposite(editor);
    return composite == null ? Collections.emptyList() : composite.getTopComponents(editor);
  }

  @Override
  public void addTopComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getComposite(editor);
    if (composite != null) {
      composite.addTopComponent(editor, component);
    }
  }

  @Override
  public void removeTopComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getComposite(editor);
    if (composite != null) {
      composite.removeTopComponent(editor, component);
    }
  }

  @Override
  public void addBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getComposite(editor);
    if (composite != null) {
      composite.addBottomComponent(editor, component);
    }
  }

  @Override
  public void removeBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorComposite composite = getComposite(editor);
    if (composite != null) {
      composite.removeBottomComponent(editor, component);
    }
  }

  @Override
  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
    myListenerList.add(listener);
  }

  @Override
  public void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
    myListenerList.remove(listener);
  }

  @ApiStatus.Internal
  public final @NotNull EditorsSplitters init() {
    //myFocusWatcher.install(myWindows.getComponent ());
    EditorsSplitters splitters = initUI();
    splitters.startListeningFocus();

    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      // updates tabs colors
      fileStatusManager.addFileStatusListener(new MyFileStatusListener(), myProject);
    }

    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootsListener());
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, new MyRootsListener());

    // updates tabs names
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new MyVirtualFileListener());

    // extends/cuts number of opened tabs. Also updates location of tabs
    connection.subscribe(UISettingsListener.TOPIC, new MyUISettingsListener());
    return splitters;
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

  public @Nullable EditorComposite getComposite(@NotNull FileEditor editor) {
    for (EditorsSplitters splitters : getAllSplitters()) {
      List<EditorComposite> editorsComposites = splitters.getAllComposites();
      for (int i = editorsComposites.size() - 1; i >= 0; i--) {
        EditorComposite composite = editorsComposites.get(i);
        if (composite.getAllEditors().contains(editor)) return composite;
      }
    }
    for (ClientFileEditorManager clientManager: getAllClientFileEditorManagers()) {
      EditorComposite composite = clientManager.getComposite(editor);
      if (composite != null) return composite;
    }

    return null;
  }

  private static void assertDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  @ApiStatus.Internal
  public void fireSelectionChanged(@Nullable EditorComposite newSelectedComposite) {
    @Nullable EditorComposite composite = SoftReference.dereference(myLastSelectedComposite);
    FileEditorWithProvider oldEditorWithProvider = composite == null ? null : composite.getSelectedWithProvider();
    FileEditorWithProvider newEditorWithProvider = newSelectedComposite == null ? null : newSelectedComposite.getSelectedWithProvider();
    myLastSelectedComposite = newSelectedComposite == null ? null : new WeakReference<>(newSelectedComposite);
    if (!Objects.equals(oldEditorWithProvider, newEditorWithProvider)) {
      FileEditorManagerEvent event = new FileEditorManagerEvent(this, oldEditorWithProvider, newEditorWithProvider);
      FileEditorManagerListener publisher = getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);

      if (newEditorWithProvider != null) {
        JComponent component =  newEditorWithProvider.getFileEditor().getComponent();
        EditorWindowHolder holder =
          ComponentUtil.getParentOfType((Class<? extends EditorWindowHolder>)EditorWindowHolder.class, (Component)component);
        VirtualFile file = newEditorWithProvider.getFileEditor().getFile();
        if (holder != null && file != null) {
          addSelectionRecord(file, holder.getEditorWindow());
        }
      }
      notifyPublisher(() -> publisher.selectionChanged(event));
    }
  }

  protected static @NotNull VirtualFile getOriginalFile(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    return BackedVirtualFile.getOriginFileIfBacked(file);
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

  protected void disposeComposite(@NotNull EditorComposite composite) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      ClientFileEditorManager clientManager = getClientFileEditorManager();
      if (clientManager != null) {
        clientManager.removeComposite(composite);
      }
      return;
    }

    myOpenedComposites.remove(composite);

    if (getAllEditors().length == 0) {
      setCurrentWindow(null);
    }

    if (composite.equals(getLastSelected())) {
      composite.getSelectedEditor().deselectNotify();
      getSplitters().setCurrentWindow(null, false);
    }

    List<FileEditorWithProvider> editorsWithProviders = composite.getAllEditorsWithProviders();

    FileEditor selectedEditor = composite.getSelectedEditor();
    for (FileEditorWithProvider editorWithProvider : ContainerUtil.reverse(editorsWithProviders)) {
      FileEditor editor = editorWithProvider.getFileEditor();
      FileEditorProvider provider = editorWithProvider.getProvider();
      // we already notified the myEditor (when fire event)
      if (selectedEditor.equals(editor)) {
        editor.deselectNotify();
      }
      editor.removePropertyChangeListener(myEditorPropertyChangeListener);
      provider.disposeEditor(editor);
    }

    Disposer.dispose(composite);
  }

  private @Nullable EditorComposite getLastSelected() {
    EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
    if (currentWindow != null) {
      return currentWindow.getSelectedComposite();
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
      splitters.insideChange++;
      try {
        change.run(splitters);
      }
      finally {
        splitters.insideChange--;

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
    public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          beforeFileDeletion((VFileDeleteEvent)event);
        }
      }
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
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
      VirtualFile[] openFiles = getOpenFilesWithRemotes();
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
        EditorComposite composite = getComposite(editor);
        if (composite != null) {
          updateFileIcon(composite.getFile());
        }
      }
      else if (FileEditor.PROP_VALID.equals(propertyName)) {
        boolean valid = ((Boolean)e.getNewValue()).booleanValue();
        if (!valid) {
          FileEditor editor = (FileEditor)e.getSource();
          LOG.assertTrue(editor != null);
          EditorComposite composite = getComposite(editor);
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
        updateFileIcon(file, true);
      }
    }
  }

  private class MyRootsListener implements ModuleRootListener, AdditionalLibraryRootsListener {

    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      changeHappened();
    }

    private void changeHappened() {
      if (LightEdit.owns(myProject)) return;
      AppUIExecutor
        .onUiThread(ModalityState.any())
        .expireWith(myProject)
        .submit(() -> StreamEx.of(getWindows()).toFlatList(EditorWindow::getAllComposites))
        .onSuccess(allEditors -> ReadAction
          .nonBlocking(() -> calcEditorReplacements(allEditors))
          .inSmartMode(myProject)
          .finishOnUiThread(ModalityState.defaultModalityState(), this::replaceEditors)
          .coalesceBy(this)
          .submit(AppExecutorUtil.getAppExecutorService()));
    }

    private Map<EditorComposite, Pair<VirtualFile, Integer>> calcEditorReplacements(List<EditorComposite> composites) {
      List<EditorFileSwapper> swappers = EditorFileSwapper.EP_NAME.getExtensionList();
      return StreamEx.of(composites).mapToEntry(composite -> {
        if (composite.getFile().isValid()) {
          for (EditorFileSwapper each : swappers) {
            Pair<VirtualFile, Integer> fileAndOffset = each.getFileToSwapTo(myProject, composite);
            if (fileAndOffset != null) return fileAndOffset;
          }
        }
        return null;
      }).nonNullValues().toMap();
    }

    private void replaceEditors(Map<EditorComposite, Pair<VirtualFile, Integer>> replacements) {
      if (replacements.isEmpty()) return;

      for (EditorWindow eachWindow : getWindows()) {
        EditorComposite selected = eachWindow.getSelectedComposite();
        List<EditorComposite> composites = eachWindow.getAllComposites();
        for (int i = 0; i < composites.size(); i++) {
          EditorComposite composite = composites.get(i);
          VirtualFile file = composite.getFile();
          if (!file.isValid()) continue;

          Pair<VirtualFile, Integer> newFilePair = replacements.get(composite);
          if (newFilePair == null) continue;

          VirtualFile newFile = newFilePair.first;
          if (newFile == null) continue;

          // already open
          if (eachWindow.findFileIndex(newFile) != -1) continue;

          FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
            .withIndex(i)
            .withRequestFocus(composite == selected);
          Pair<FileEditor[], FileEditorProvider[]> pair = openFileImpl2(eachWindow, newFile, openOptions);

          if (newFilePair.second != null) {
            TextEditorImpl openedEditor = EditorFileSwapper.findSinglePsiAwareEditor(pair.first);
            if (openedEditor != null) {
              openedEditor.getEditor().getCaretModel().moveToOffset(newFilePair.second);
              openedEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
            }
          }
          closeFile(file, eachWindow);
        }
      }
    }

    @Override
    public void libraryRootsChanged(@Nullable @Nls String presentableLibraryName,
                                    @NotNull Collection<? extends VirtualFile> oldRoots,
                                    @NotNull Collection<? extends VirtualFile> newRoots,
                                    @NotNull String libraryNameForDebug) {
      changeHappened();
    }
  }

  /**
   * Gets notifications from UISetting component to track changes of RECENT_FILES_LIMIT
   * and EDITOR_TAB_LIMIT, etc. values.
   */
  private final class MyUISettingsListener implements UISettingsListener {
    @Override
    public void uiSettingsChanged(@NotNull UISettings uiSettings) {
      assertDispatchThread();
      getMainSplitters().revalidate();
      for (EditorsSplitters each : getAllSplitters()) {
        each.setTabsPlacement(uiSettings.getEditorTabPlacement());
        each.trimToSize();

        // Tab layout policy
        if (uiSettings.getScrollTabLayoutInEditor()) {
          each.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        }
        else {
          each.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
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
    assertDispatchThread();

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      ourOpenFilesSetModificationCount.incrementAndGet();
      runBulkTabChange(getSplitters(), EditorsSplitters::closeAllFiles);
    }, "", null);
  }

  @Override
  public VirtualFile @NotNull [] getSiblings(@NotNull VirtualFile file) {
    return getOpenFiles();
  }

  void queueUpdateFile(@NotNull VirtualFile file) {
    queue.queue(new Update(file) {
      @Override
      public void run() {
        if (isFileOpen(file)) {
          updateFileIcon(file);
          updateFileColor(file);
          updateFileBackgroundColor(file);
          resetPreviewFlag(file);
        }
      }
    });
  }

  @Override
  public EditorsSplitters getSplittersFor(Component c) {
    EditorsSplitters splitters = null;
    DockContainer dockContainer = myDockManager.getContainerFor(c, DockableEditorTabbedContainer.class::isInstance);
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
