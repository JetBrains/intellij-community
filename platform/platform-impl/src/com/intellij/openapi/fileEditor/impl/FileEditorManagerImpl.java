/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.preview.PreviewManager;
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
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.reference.SoftReference;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.impl.DockManagerImpl;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.impl.MessageListenerList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Anton Katilin
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
@State(
  name = "FileEditorManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class FileEditorManagerImpl extends FileEditorManagerEx implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl");
  private static final Key<Boolean> DUMB_AWARE = Key.create("DUMB_AWARE");

  private static final FileEditor[] EMPTY_EDITOR_ARRAY = {};
  private static final FileEditorProvider[] EMPTY_PROVIDER_ARRAY = {};
  public static final Key<Boolean> CLOSING_TO_REOPEN = Key.create("CLOSING_TO_REOPEN");
  public static final String FILE_EDITOR_MANAGER = "FileEditorManager";

  private volatile JPanel myPanels;
  private EditorsSplitters mySplitters;
  private final Project myProject;
  private final List<Pair<VirtualFile, EditorWindow>> mySelectionHistory = new ArrayList<>();
  private Reference<EditorComposite> myLastSelectedComposite = new WeakReference<>(null);

  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("FileEditorManagerUpdateQueue", 50, true,
                                                                    MergingUpdateQueue.ANY_COMPONENT);

  private final BusyObject.Impl.Simple myBusyObject = new BusyObject.Impl.Simple();

  /**
   * Removes invalid myEditor and updates "modified" status.
   */
  private final PropertyChangeListener myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();
  private final DockManager myDockManager;
  private DockableEditorContainerFactory myContentFactory;
  private static final AtomicInteger ourOpenFilesSetModificationCount = new AtomicInteger();

  static final ModificationTracker OPEN_FILE_SET_MODIFICATION_COUNT = ourOpenFilesSetModificationCount::get;


  public FileEditorManagerImpl(@NotNull Project project, DockManager dockManager) {
/*    ApplicationManager.getApplication().assertIsDispatchThread(); */
    myProject = project;
    myDockManager = dockManager;
    myListenerList =
      new MessageListenerList<>(myProject.getMessageBus(), FileEditorManagerListener.FILE_EDITOR_MANAGER);

    if (Extensions.getExtensions(FileEditorAssociateFinder.EP_NAME).length > 0) {
      myListenerList.add(new FileEditorManagerListener() {
        @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
          EditorsSplitters splitters = getSplitters();
          openAssociatedFile(event.getNewFile(), splitters.getCurrentWindow(), splitters);
        }
      });
    }

    myQueue.setTrackUiActivity(true);

    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
      }

      @Override
      public void exitDumbMode() {
        // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!myProject.isDisposed())
            dumbModeFinished(myProject);
        });
      }
    });
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        if (project == myProject) {
          FileEditorManagerImpl.this.projectOpened(connection);
        }
      }

      @Override
      public void projectClosed(Project project) {
        if (project == myProject) {
          // Dispose created editors. We do not use use closeEditor method because
          // it fires event and changes history.
          closeAllFiles();
        }
      }
    });
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
      if (newProviders.length > providers.size()) {
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
    }
  }

  public void initDockableContentFactory() {
    if (myContentFactory != null) return;

    myContentFactory = new DockableEditorContainerFactory(myProject, this, myDockManager);
    myDockManager.register(DockableEditorContainerFactory.TYPE, myContentFactory);
    Disposer.register(myProject, myContentFactory);
  }

  public static boolean isDumbAware(@NotNull FileEditor editor) {
    return Boolean.TRUE.equals(editor.getUserData(DUMB_AWARE)) &&
           (!(editor instanceof PossiblyDumbAware) || ((PossiblyDumbAware)editor).isDumbAware());
  }

  //-------------------------------------------------------------------------------

  @Override
  public JComponent getComponent() {
    initUI();
    return myPanels;
  }

  @NotNull
  public EditorsSplitters getMainSplitters() {
    initUI();

    return mySplitters;
  }

  @NotNull
  public Set<EditorsSplitters> getAllSplitters() {
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

  @NotNull
  private AsyncResult<EditorsSplitters> getActiveSplittersAsync() {
    final AsyncResult<EditorsSplitters> result = new AsyncResult<>();
    final IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    fm.doWhenFocusSettlesDown(() -> {
      if (myProject.isDisposed()) {
        result.setRejected();
        return;
      }
      Component focusOwner = fm.getFocusOwner();
      DockContainer container = myDockManager.getContainerFor(focusOwner);
      if (container instanceof DockableEditorTabbedContainer) {
        result.setDone(((DockableEditorTabbedContainer)container).getSplitters());
      }
      else {
        result.setDone(getMainSplitters());
      }
    });
    return result;
  }

  private EditorsSplitters getActiveSplittersSync() {
    assertDispatchThread();

    final IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component focusOwner = fm.getFocusOwner();
    if (focusOwner == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }
    if (focusOwner == null) {
      focusOwner = fm.getLastFocusedFor(fm.getLastFocusedFrame());
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

  private void initUI() {
    if (myPanels == null) {
      synchronized (myInitLock) {
        if (myPanels == null) {
          final JPanel panel = new JPanel(new BorderLayout());
          panel.setOpaque(false);
          panel.setBorder(new MyBorder());
          mySplitters = new EditorsSplitters(this, myDockManager, true);
          Disposer.register(myProject, mySplitters);
          panel.add(mySplitters, BorderLayout.CENTER);
          myPanels = panel;
        }
      }
    }
  }

  private static class MyBorder implements Border {
    @Override
    public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
      if (UIUtil.isUnderAquaLookAndFeel()) {
        g.setColor(JBTabsImpl.MAC_AQUA_BG_COLOR);
        final Insets insets = getBorderInsets(c);
        if (insets.top > 0) {
          g.fillRect(x, y, width, height + insets.top);
        }
      }
    }

    @NotNull
    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.emptyInsets();
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    assertReadAccess();
    final EditorWindow window = getSplitters().getCurrentWindow();
    if (window != null) {
      final EditorWithProviderComposite editor = window.getSelectedEditor();
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
  public Color getFileColor(@NotNull final VirtualFile file) {
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    Color statusColor = fileStatusManager != null ? fileStatusManager.getStatus(file).getColor() : UIUtil.getLabelForeground();
    if (statusColor == null) statusColor = UIUtil.getLabelForeground();
    return statusColor;
  }

  public boolean isProblem(@NotNull final VirtualFile file) {
    return false;
  }

  @NotNull
  public String getFileTooltipText(@NotNull VirtualFile file) {
    return FileUtil.getLocationRelativeToUserHome(file.getPresentableUrl());
  }

  @Override
  public void updateFilePresentation(@NotNull VirtualFile file) {
    if (!isFileOpen(file)) return;

    updateFileColor(file);
    updateFileIcon(file);
    updateFileName(file);
    updateFileBackgroundColor(file);
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
  void updateFileName(@Nullable final VirtualFile file) {
    // Queue here is to prevent title flickering when tab is being closed and two events arriving: with component==null and component==next focused tab
    // only the last event makes sense to handle
    myQueue.queue(new Update("UpdateFileName " + (file == null ? "" : file.getPath())) {
      @Override
      public boolean isExpired() {
        return myProject.isDisposed() || !myProject.isOpen() || (file == null ? super.isExpired() : !file.isValid());
      }

      @Override
      public void run() {
        Set<EditorsSplitters> all = getAllSplitters();
        for (EditorsSplitters each : all) {
          each.updateFileName(file);
        }
      }
    });
  }

  //-------------------------------------------------------


  @Override
  public VirtualFile getFile(@NotNull final FileEditor editor) {
    final EditorComposite editorComposite = getEditorComposite(editor);
    if (editorComposite != null) {
      return editorComposite.getFile();
    }
    return null;
  }

  @Override
  public void unsplitWindow() {
    final EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplit(true);
    }
  }

  @Override
  public void unsplitAllWindow() {
    final EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
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
  @NotNull
  public EditorWindow[] getWindows() {
    List<EditorWindow> windows = new ArrayList<>();
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      EditorWindow[] eachList = each.getWindows();
      ContainerUtil.addAll(windows, eachList);
    }

    return windows.toArray(new EditorWindow[windows.size()]);
  }

  @Override
  public EditorWindow getNextWindow(@NotNull final EditorWindow window) {
    final EditorWindow[] windows = getSplitters().getOrderedWindows();
    for (int i = 0; i != windows.length; ++i) {
      if (windows[i].equals(window)) {
        return windows[(i + 1) % windows.length];
      }
    }
    LOG.error("Not window found");
    return null;
  }

  @Override
  public EditorWindow getPrevWindow(@NotNull final EditorWindow window) {
    final EditorWindow[] windows = getSplitters().getOrderedWindows();
    for (int i = 0; i != windows.length; ++i) {
      if (windows[i].equals(window)) {
        return windows[(i + windows.length - 1) % windows.length];
      }
    }
    LOG.error("Not window found");
    return null;
  }

  @Override
  public void createSplitter(final int orientation, @Nullable final EditorWindow window) {
    // window was available from action event, for example when invoked from the tab menu of an editor that is not the 'current'
    if (window != null) {
      window.split(orientation, true, null, false);
    }
    // otherwise we'll split the current window, if any
    else {
      final EditorWindow currentWindow = getSplitters().getCurrentWindow();
      if (currentWindow != null) {
        currentWindow.split(orientation, true, null, false);
      }
    }
  }

  @Override
  public void changeSplitterOrientation() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.changeOrientation();
    }
  }


  @Override
  public void flipTabs() {
    /*
    if (myTabs == null) {
      myTabs = new EditorTabs (this, UISettings.getInstance().getEditorTabPlacement());
      remove (mySplitters);
      add (myTabs, BorderLayout.CENTER);
      initTabs ();
    } else {
      remove (myTabs);
      add (mySplitters, BorderLayout.CENTER);
      myTabs.dispose ();
      myTabs = null;
    }
    */
    myPanels.revalidate();
  }

  @Override
  public boolean tabsMode() {
    return false;
  }

  private void setTabsMode(final boolean mode) {
    if (tabsMode() != mode) {
      flipTabs();
    }
    //LOG.assertTrue (tabsMode () == mode);
  }


  @Override
  public boolean isInSplitter() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    return currentWindow != null && currentWindow.inSplitter();
  }

  @Override
  public boolean hasOpenedFile() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    return currentWindow != null && currentWindow.getSelectedEditor() != null;
  }

  @Override
  public VirtualFile getCurrentFile() {
    return getActiveSplittersSync().getCurrentFile();
  }

  @Override
  @NotNull
  public AsyncResult<EditorWindow> getActiveWindow() {
    return getActiveSplittersAsync().subResult(EditorsSplitters::getCurrentWindow);
  }

  @Override
  public EditorWindow getCurrentWindow() {
    if (!ApplicationManager.getApplication().isDispatchThread()) return null;
    EditorsSplitters splitters = getActiveSplittersSync();
    return splitters == null ? null : splitters.getCurrentWindow();
  }

  @Override
  public void setCurrentWindow(final EditorWindow window) {
    getActiveSplittersSync().setCurrentWindow(window, true);
  }

  public void closeFile(@NotNull final VirtualFile file, @NotNull final EditorWindow window, final boolean transferFocus) {
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
  public void closeFile(@NotNull final VirtualFile file, @NotNull final EditorWindow window) {
    closeFile(file, window, true);
  }

  //============================= EditorManager methods ================================

  @Override
  public void closeFile(@NotNull final VirtualFile file) {
    closeFile(file, true, false);
  }

  public void closeFile(@NotNull final VirtualFile file, final boolean moveFocus, final boolean closeAllCopies) {
    assertDispatchThread();

    CommandProcessor.getInstance().executeCommand(myProject, () -> closeFileImpl(file, moveFocus, closeAllCopies), "", null);
  }

  private void closeFileImpl(@NotNull final VirtualFile file, final boolean moveFocus, boolean closeAllCopies) {
    assertDispatchThread();
    ourOpenFilesSetModificationCount.incrementAndGet();
    runChange(splitters -> splitters.closeFile(file, moveFocus), closeAllCopies ? null : getActiveSplittersSync());
  }

  //-------------------------------------- Open File ----------------------------------------

  @Override
  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull final VirtualFile file,
                                                                        boolean focusEditor,
                                                                        final boolean searchForSplitter) {
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertDispatchThread();

    if (isOpenInNewWindow()) {
      return openFileInNewWindow(file);
    }


    EditorWindow wndToOpenIn = null;
    if (searchForSplitter) {
      Set<EditorsSplitters> all = getAllSplitters();
      EditorsSplitters active = getActiveSplittersSync();
      if (active.getCurrentWindow() != null && active.getCurrentWindow().isFileOpen(file)) {
        wndToOpenIn = active.getCurrentWindow();
      } else {
        for (EditorsSplitters splitters : all) {
          final EditorWindow window = splitters.getCurrentWindow();
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

    if (wndToOpenIn == null || !wndToOpenIn.isFileOpen(file)) {
      Pair<FileEditor[], FileEditorProvider[]> previewResult =
        PreviewManager.SERVICE.preview(myProject, FilePreviewPanelProvider.ID, file, focusEditor);
        if (previewResult != null) {
          return previewResult;
        }
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
        ((MouseEvent)event).isShiftDown() &&
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
      for (FileEditorAssociateFinder finder : Extensions.getExtensions(FileEditorAssociateFinder.EP_NAME)) {
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

  @NotNull
  @Override
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                        boolean focusEditor,
                                                                        @NotNull EditorWindow window) {
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertDispatchThread();

    return openFileImpl2(window, file, focusEditor);
  }

  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(@NotNull final EditorWindow window,
                                                                @NotNull final VirtualFile file,
                                                                final boolean focusEditor) {
    final Ref<Pair<FileEditor[], FileEditorProvider[]>> result = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> result.set(openFileImpl3(window, file, focusEditor, null, true)), "", null);
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
  @NotNull
  Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(@NotNull final EditorWindow window,
                                                         @NotNull final VirtualFile file,
                                                         final boolean focusEditor,
                                                         @Nullable final HistoryEntry entry,
                                                         boolean current) {
    return openFileImpl4(window, file, entry, current, focusEditor, null, -1);
  }

  /**
   * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
   */
  @NotNull
  Pair<FileEditor[], FileEditorProvider[]> openFileImpl4(@NotNull final EditorWindow window,
                                                         @NotNull final VirtualFile file,
                                                         @Nullable final HistoryEntry entry,
                                                         final boolean current,
                                                         final boolean focusEditor,
                                                         final Boolean pin,
                                                         final int index) {
    assert ApplicationManager.getApplication().isDispatchThread() || !ApplicationManager.getApplication().isReadAccessAllowed() : "must not open files under read action since we are doing a lot of invokeAndWaits here";

    final Ref<EditorWithProviderComposite> compositeRef = new Ref<>();

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> compositeRef.set(window.findFileComposite(file)));

    final FileEditorProvider[] newProviders;
    final AsyncFileEditorProvider.Builder[] builders;
    if (compositeRef.isNull()) {
      // File is not opened yet. In this case we have to create editors
      // and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProviders(myProject, file);
      if (newProviders.length == 0) {
        return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
      }

      builders = new AsyncFileEditorProvider.Builder[newProviders.length];
      for (int i = 0; i < newProviders.length; i++) {
        try {
          final FileEditorProvider provider = newProviders[i];
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
    Runnable runnable = () -> {
      if (myProject.isDisposed() || !file.isValid()) {
        return;
      }

      HeavyProcessLatch.INSTANCE.prioritizeUiActivity();
      ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

      compositeRef.set(window.findFileComposite(file));
      boolean newEditor = compositeRef.isNull();
      if (newEditor) {
        clearWindowIfNeeded(window);

        getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileOpened(this, file);

        FileEditor[] newEditors = new FileEditor[newProviders.length];
        for (int i = 0; i < newProviders.length; i++) {
          try {
            final FileEditorProvider provider = newProviders[i];
            final FileEditor editor = builders[i] == null ? provider.createEditor(myProject, file) : builders[i].build();
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

        if (index >= 0) {
          composite.getFile().putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
        }

        compositeRef.set(composite);
      }

      final EditorWithProviderComposite composite = compositeRef.get();
      FileEditor[] editors = composite.getEditors();
      FileEditorProvider[] providers = composite.getProviders();

      window.setEditor(composite, current, focusEditor);

      for (int i = 0; i < editors.length; i++) {
        restoreEditorState(file, providers[i], editors[i], entry, newEditor);
      }

      // Restore selected editor
      final FileEditorProvider selectedProvider;
      if (entry == null) {
        selectedProvider = ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance())
          .getSelectedFileEditorProvider(EditorHistoryManager.getInstance(myProject), file, providers);
      }
      else {
        selectedProvider = entry.getSelectedProvider();
      }
      if (selectedProvider != null) {
        for (int i = editors.length - 1; i >= 0; i--) {
          final FileEditorProvider provider = providers[i];
          if (provider.equals(selectedProvider)) {
            composite.setSelectedEditor(i);
            break;
          }
        }
      }

      // Notify editors about selection changes
      window.getOwner().setCurrentWindow(window, focusEditor);
      window.getOwner().afterFileOpen(file);
      addSelectionRecord(file, window);

      composite.getSelectedEditor().selectNotify();

      // Transfer focus into editor
      if (!ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
        if (focusEditor) {
          //myFirstIsActive = myTabbedContainer1.equals(tabbedContainer);
          window.setAsCurrentWindow(true);
          ToolWindowManager.getInstance(myProject).activateEditorComponent();
          IdeFocusManager.getInstance(myProject).toFront(window.getOwner());
        }
      }

      if (newEditor) {
        notifyPublisher(() -> {
          if (isFileOpen(file)) {
            getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
              .fileOpened(this, file);
          }
        });
        ourOpenFilesSetModificationCount.incrementAndGet();
      }

      //[jeka] this is a hack to support back-forward navigation
      // previously here was incorrect call to fireSelectionChanged() with a side-effect
      ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();

      // Update frame and tab title
      updateFileName(file);

      // Make back/forward work
      IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();

      if (pin != null) {
        window.setFilePinned(file, pin);
      }
    };

    ApplicationManager.getApplication().invokeAndWait(runnable);

    EditorWithProviderComposite composite = compositeRef.get();
    return Pair.create(composite == null ? EMPTY_EDITOR_ARRAY : composite.getEditors(),
                       composite == null ? EMPTY_PROVIDER_ARRAY : composite.getProviders());
  }

  @Nullable
  private EditorWithProviderComposite createComposite(@NotNull VirtualFile file,
                                                      @NotNull FileEditor[] editors, @NotNull FileEditorProvider[] providers) {
    if (NullUtils.hasNull(editors) || NullUtils.hasNull(providers)) {
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
      editors = editorList.toArray(new FileEditor[editorList.size()]);
      providers = providerList.toArray(new FileEditorProvider[providerList.size()]);
    }
    return new EditorWithProviderComposite(file, editors, providers, this);
  }

  private static void clearWindowIfNeeded(@NotNull EditorWindow window) {
    if (UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE || UISettings.getInstance().getPresentationMode()) {
      window.clear();
    }
  }

  private void restoreEditorState(@NotNull VirtualFile file,
                                  @NotNull FileEditorProvider provider,
                                  @NotNull final FileEditor editor,
                                  HistoryEntry entry,
                                  boolean newEditor) {
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
        final FileEditorState finalState = state;
        DumbService.getInstance(getProject()).runWhenSmart(() -> editor.setState(finalState));
      }
      else {
        editor.setState(state);
      }
    }
  }

  @NotNull
  @Override
  public ActionCallback notifyPublisher(@NotNull final Runnable runnable) {
    final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    final ActionCallback done = new ActionCallback();
    return myBusyObject.execute(new ActiveRunnable() {
      @NotNull
      @Override
      public ActionCallback run() {
        focusManager.doWhenFocusSettlesDown(new ExpirableRunnable.ForProject(myProject) {
          @Override
          public void run() {
            runnable.run();
            done.setDone();
          }
        }, ModalityState.current());
        return done;
      }
    });
  }

  @Override
  public void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId) {
    EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite == null) {
      final List<EditorWithProviderComposite> composites = getEditorComposites(file);

      if (composites.isEmpty()) return;
      composite = composites.get(0);
    }

    final FileEditorProvider[] editorProviders = composite.getProviders();
    final FileEditorProvider selectedProvider = composite.getSelectedEditorWithProvider().getSecond();

    for (int i = 0; i < editorProviders.length; i++) {
      if (editorProviders[i].getEditorTypeId().equals(fileEditorProviderId) && !selectedProvider.equals(editorProviders[i])) {
        composite.setSelectedEditor(i);
        composite.getSelectedEditor().selectNotify();
      }
    }
  }


  @Nullable
  EditorWithProviderComposite newEditorComposite(final VirtualFile file) {
    if (file == null) {
      return null;
    }

    final FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    final FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
    if (providers.length == 0) return null;
    final FileEditor[] editors = new FileEditor[providers.length];
    for (int i = 0; i < providers.length; i++) {
      final FileEditorProvider provider = providers[i];
      LOG.assertTrue(provider != null);
      LOG.assertTrue(provider.accept(myProject, file));
      final FileEditor editor = provider.createEditor(myProject, file);
      editors[i] = editor;
      LOG.assertTrue(editor.isValid());
      editor.addPropertyChangeListener(myEditorPropertyChangeListener);
    }

    final EditorWithProviderComposite newComposite = new EditorWithProviderComposite(file, editors, providers, this);
    final EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(myProject);
    for (int i = 0; i < editors.length; i++) {
      final FileEditor editor = editors[i];

      final FileEditorProvider provider = providers[i];

// Restore myEditor state
      FileEditorState state = editorHistoryManager.getState(file, provider);
      if (state != null) {
        editor.setState(state);
      }
    }
    return newComposite;
  }

  @Override
  @NotNull
  public List<FileEditor> openEditor(@NotNull final OpenFileDescriptor descriptor, final boolean focusEditor) {
    assertDispatchThread();
    if (descriptor.getFile() instanceof VirtualFileWindow) {
      VirtualFileWindow delegate = (VirtualFileWindow)descriptor.getFile();
      int hostOffset = delegate.getDocumentWindow().injectedToHost(descriptor.getOffset());
      OpenFileDescriptor realDescriptor = new OpenFileDescriptor(descriptor.getProject(), delegate.getDelegate(), hostOffset);
      realDescriptor.setUseCurrentWindow(descriptor.isUseCurrentWindow());
      return openEditor(realDescriptor, focusEditor);
    }

    final List<FileEditor> result = new SmartList<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      VirtualFile file = descriptor.getFile();
      final FileEditor[] editors = openFile(file, focusEditor, !descriptor.isUseCurrentWindow());
      ContainerUtil.addAll(result, editors);

      boolean navigated = false;
      for (final FileEditor editor : editors) {
        if (editor instanceof NavigatableFileEditor &&
            getSelectedEditor(descriptor.getFile()) == editor) { // try to navigate opened editor
          navigated = navigateAndSelectEditor((NavigatableFileEditor)editor, descriptor);
          if (navigated) break;
        }
      }

      if (!navigated) {
        for (final FileEditor editor : editors) {
          if (editor instanceof NavigatableFileEditor && getSelectedEditor(descriptor.getFile()) != editor) { // try other editors
            if (navigateAndSelectEditor((NavigatableFileEditor)editor, descriptor)) {
              break;
            }
          }
        }
      }
    }, "", null);

    return result;
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
    final EditorWithProviderComposite composite = getEditorComposite(editor);
    if (composite == null) return;

    final FileEditor[] editors = composite.getEditors();
    for (int i = 0; i < editors.length; i++) {
      final FileEditor each = editors[i];
      if (editor == each) {
        composite.setSelectedEditor(i);
        composite.getSelectedEditor().selectNotify();
        break;
      }
    }
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public Editor openTextEditor(@NotNull final OpenFileDescriptor descriptor, final boolean focusEditor) {
    final Collection<FileEditor> fileEditors = openEditor(descriptor, focusEditor);
    for (FileEditor fileEditor : fileEditors) {
      if (fileEditor instanceof TextEditor) {
        setSelectedEditor(descriptor.getFile(), TextEditorProvider.getInstance().getEditorTypeId());
        Editor editor = ((TextEditor)fileEditor).getEditor();
        return getOpenedEditor(editor, focusEditor);
      }
    }

    return null;
  }

  protected Editor getOpenedEditor(@NotNull Editor editor, final boolean focusEditor) {
    return editor;
  }

  @Override
  public Editor getSelectedTextEditor() {
    return getSelectedTextEditor(false);
  }

  public Editor getSelectedTextEditor(boolean lockfree) {
    if (!lockfree) {
      assertDispatchThread();
    }

    final EditorWindow currentWindow = lockfree ? getMainSplitters().getCurrentWindow() : getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      final EditorWithProviderComposite selectedEditor = currentWindow.getSelectedEditor();
      if (selectedEditor != null && selectedEditor.getSelectedEditor() instanceof TextEditor) {
        return ((TextEditor)selectedEditor.getSelectedEditor()).getEditor();
      }
    }

    return null;
  }

  @Override
  public boolean isFileOpen(@NotNull final VirtualFile file) {
    return !getEditorComposites(file).isEmpty();
  }

  @Override
  @NotNull
  public VirtualFile[] getOpenFiles() {
    Set<VirtualFile> openFiles = new THashSet<>();
    for (EditorsSplitters each : getAllSplitters()) {
      ContainerUtil.addAll(openFiles, each.getOpenFiles());
    }
    return VfsUtilCore.toVirtualFileArray(openFiles);
  }

  @Override
  @NotNull
  public VirtualFile[] getSelectedFiles() {
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
  @NotNull
  public FileEditor[] getSelectedEditors() {
    Set<FileEditor> selectedEditors = new HashSet<>();
    for (EditorsSplitters each : getAllSplitters()) {
      ContainerUtil.addAll(selectedEditors, each.getSelectedEditors());
    }

    return selectedEditors.toArray(new FileEditor[selectedEditors.size()]);
  }

  @Override
  @NotNull
  public EditorsSplitters getSplitters() {
    EditorsSplitters active = null;
    if (ApplicationManager.getApplication().isDispatchThread()) active = getActiveSplittersSync();
    return active == null ? getMainSplitters() : active;
  }

  @Override
  @Nullable
  public FileEditor getSelectedEditor(@NotNull final VirtualFile file) {
    final Pair<FileEditor, FileEditorProvider> selectedEditorWithProvider = getSelectedEditorWithProvider(file);
    return selectedEditorWithProvider == null ? null : selectedEditorWithProvider.getFirst();
  }


  @Override
  @Nullable
  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    final EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return composite.getSelectedEditorWithProvider();
    }

    final List<EditorWithProviderComposite> composites = getEditorComposites(file);
    return composites.isEmpty() ? null : composites.get(0).getSelectedEditorWithProvider();
  }

  @Override
  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull final VirtualFile file) {
    assertReadAccess();

    final EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return Pair.create(composite.getEditors(), composite.getProviders());
    }

    final List<EditorWithProviderComposite> composites = getEditorComposites(file);
    if (!composites.isEmpty()) {
      return Pair.create(composites.get(0).getEditors(), composites.get(0).getProviders());
    }
    return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
  }

  @Override
  @NotNull
  public FileEditor[] getEditors(@NotNull VirtualFile file) {
    assertReadAccess();
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();

    final EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return composite.getEditors();
    }

    final List<EditorWithProviderComposite> composites = getEditorComposites(file);
    if (!composites.isEmpty()) {
      return composites.get(0).getEditors();
    }
    return EMPTY_EDITOR_ARRAY;
  }

  @NotNull
  @Override
  public FileEditor[] getAllEditors(@NotNull VirtualFile file) {
    List<EditorWithProviderComposite> editorComposites = getEditorComposites(file);
    if (editorComposites.isEmpty()) return EMPTY_EDITOR_ARRAY;
    List<FileEditor> editors = new ArrayList<>();
    for (EditorWithProviderComposite composite : editorComposites) {
      ContainerUtil.addAll(editors, composite.getEditors());
    }
    return editors.toArray(new FileEditor[editors.size()]);
  }

  @Nullable
  private EditorWithProviderComposite getCurrentEditorWithProviderComposite(@NotNull final VirtualFile virtualFile) {
    final EditorWindow editorWindow = getSplitters().getCurrentWindow();
    if (editorWindow != null) {
      return editorWindow.findFileComposite(virtualFile);
    }
    return null;
  }

  @NotNull
  private List<EditorWithProviderComposite> getEditorComposites(@NotNull VirtualFile file) {
    List<EditorWithProviderComposite> result = new ArrayList<>();
    Set<EditorsSplitters> all = getAllSplitters();
    for (EditorsSplitters each : all) {
      result.addAll(each.findEditorComposites(file));
    }
    return result;
  }

  @Override
  @NotNull
  public FileEditor[] getAllEditors() {
    assertReadAccess();
    List<FileEditor> result = new ArrayList<>();
    final Set<EditorsSplitters> allSplitters = getAllSplitters();
    for (EditorsSplitters splitter : allSplitters) {
      final EditorWithProviderComposite[] editorsComposites = splitter.getEditorsComposites();
      for (EditorWithProviderComposite editorsComposite : editorsComposites) {
        final FileEditor[] editors = editorsComposite.getEditors();
        ContainerUtil.addAll(result, editors);
      }
    }
    return result.toArray(new FileEditor[result.size()]);
  }

  @Override
  public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
    addTopComponent(editor, annotationComponent);
  }

  @Override
  public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
    removeTopComponent(editor, annotationComponent);
  }

  @NotNull
  public List<JComponent> getTopComponents(@NotNull FileEditor editor) {
    final EditorComposite composite = getEditorComposite(editor);
    return composite != null ? composite.getTopComponents(editor) : Collections.emptyList();
  }

  @Override
  public void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.addTopComponent(editor, component);
    }
  }

  @Override
  public void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.removeTopComponent(editor, component);
    }
  }

  @Override
  public void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.addBottomComponent(editor, component);
    }
  }

  @Override
  public void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.removeBottomComponent(editor, component);
    }
  }

  private final MessageListenerList<FileEditorManagerListener> myListenerList;

  @Override
  public void addFileEditorManagerListener(@NotNull final FileEditorManagerListener listener) {
    myListenerList.add(listener);
  }

  @Override
  public void addFileEditorManagerListener(@NotNull final FileEditorManagerListener listener, @NotNull final Disposable parentDisposable) {
    myListenerList.add(listener, parentDisposable);
  }

  @Override
  public void removeFileEditorManagerListener(@NotNull final FileEditorManagerListener listener) {
    myListenerList.remove(listener);
  }

  protected void projectOpened(@NotNull MessageBusConnection connection) {
    //myFocusWatcher.install(myWindows.getComponent ());
    getMainSplitters().startListeningFocus();

    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      /*
        Updates tabs colors
       */
      final MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
      fileStatusManager.addFileStatusListener(myFileStatusListener, myProject);
    }
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootsListener());

    /*
      Updates tabs names
     */
    final MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener, myProject);
    /*
      Extends/cuts number of opened tabs. Also updates location of tabs.
     */
    connection.subscribe(UISettingsListener.TOPIC, new MyUISettingsListener());

    StartupManager.getInstance(myProject).registerPostStartupActivity((DumbAwareRunnable)() -> {
      if (myProject.isDisposed()) return;
      setTabsMode(UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE);

      ToolWindowManager.getInstance(myProject).invokeLater(() -> {
        if (!myProject.isDisposed()) {
          CommandProcessor.getInstance().executeCommand(myProject, () -> {
            ApplicationManager.getApplication().invokeLater(() -> {
              long currentTime = System.nanoTime();
              Long startTime = myProject.getUserData(ProjectImpl.CREATION_TIME);
              if (startTime != null) {
                LOG.info("Project opening took " + (currentTime - startTime.longValue()) / 1000000 + " ms");
                PluginManagerCore.dumpPluginClassStatistics();
              }
            }, myProject.getDisposed());
            // group 1
          }, "", null);
        }
      });
    });
  }

  @Nullable
  @Override
  public Element getState() {
    if (mySplitters == null) {
      // do not save if not initialized yet
      return null;
    }

    Element state = new Element("state");
    getMainSplitters().writeExternal(state);
    return state;
  }

  @Override
  public void loadState(Element state) {
    getMainSplitters().readExternal(state);
  }

  @Nullable
  private EditorWithProviderComposite getEditorComposite(@NotNull final FileEditor editor) {
    for (EditorsSplitters splitters : getAllSplitters()) {
      final EditorWithProviderComposite[] editorsComposites = splitters.getEditorsComposites();
      for (int i = editorsComposites.length - 1; i >= 0; i--) {
        final EditorWithProviderComposite composite = editorsComposites[i];
        final FileEditor[] editors = composite.getEditors();
        for (int j = editors.length - 1; j >= 0; j--) {
          final FileEditor _editor = editors[j];
          LOG.assertTrue(_editor != null);
          if (editor.equals(_editor)) {
            return composite;
          }
        }
      }
    }
    return null;
  }

//======================= Misc =====================

  private static void assertDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  public void fireSelectionChanged(final EditorComposite newSelectedComposite) {
    final Trinity<VirtualFile, FileEditor, FileEditorProvider> oldData = extract(SoftReference.dereference(myLastSelectedComposite));
    final Trinity<VirtualFile, FileEditor, FileEditorProvider> newData = extract(newSelectedComposite);
    myLastSelectedComposite = newSelectedComposite == null ? null : new WeakReference<>(newSelectedComposite);
    final boolean filesEqual = oldData.first == null ? newData.first == null : oldData.first.equals(newData.first);
    final boolean editorsEqual = oldData.second == null ? newData.second == null : oldData.second.equals(newData.second);
    if (!filesEqual || !editorsEqual) {
      if (oldData.first != null && newData.first != null) {
        for (FileEditorAssociateFinder finder : Extensions.getExtensions(FileEditorAssociateFinder.EP_NAME)) {
          VirtualFile associatedFile = finder.getAssociatedFileToOpen(myProject, oldData.first);

          if (Comparing.equal(associatedFile, newData.first)) {
            return;
          }
        }
      }

      final FileEditorManagerEvent event =
        new FileEditorManagerEvent(this, oldData.first, oldData.second, oldData.third, newData.first, newData.second, newData.third);
      final FileEditorManagerListener publisher = getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);

      if (newData.first != null) {
        final JComponent component = newData.second.getComponent();
        final EditorWindowHolder holder = UIUtil.getParentOfType(EditorWindowHolder.class, component);
        if (holder != null) {
          addSelectionRecord(newData.first, holder.getEditorWindow());
        }
      }
      notifyPublisher(() -> publisher.selectionChanged(event));
    }
  }

  @NotNull
  private static Trinity<VirtualFile, FileEditor, FileEditorProvider> extract(@Nullable EditorComposite composite) {
    final VirtualFile file;
    final FileEditor editor;
    final FileEditorProvider provider;
    if (composite == null || composite.isDisposed()) {
      file = null;
      editor = null;
      provider = null;
    }
    else {
      file = composite.getFile();
      final Pair<FileEditor, FileEditorProvider> pair = composite.getSelectedEditorWithProvider();
      editor = pair.first;
      provider = pair.second;
    }
    return new Trinity<>(file, editor, provider);
  }

  @Override
  public boolean isChanged(@NotNull final EditorComposite editor) {
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager == null) return false;
    FileStatus status = fileStatusManager.getStatus(editor.getFile());
    return status != FileStatus.UNKNOWN && status != FileStatus.NOT_CHANGED;
  }

  void disposeComposite(@NotNull EditorWithProviderComposite editor) {
    if (getAllEditors().length == 0) {
      setCurrentWindow(null);
    }

    if (editor.equals(getLastSelected())) {
      editor.getSelectedEditor().deselectNotify();
      getSplitters().setCurrentWindow(null, false);
    }

    final FileEditor[] editors = editor.getEditors();
    final FileEditorProvider[] providers = editor.getProviders();

    final FileEditor selectedEditor = editor.getSelectedEditor();
    for (int i = editors.length - 1; i >= 0; i--) {
      final FileEditor editor1 = editors[i];
      final FileEditorProvider provider = providers[i];
      if (!editor.equals(selectedEditor)) { // we already notified the myEditor (when fire event)
        if (selectedEditor.equals(editor1)) {
          editor1.deselectNotify();
        }
      }
      editor1.removePropertyChangeListener(myEditorPropertyChangeListener);
      provider.disposeEditor(editor1);
    }

    Disposer.dispose(editor);
  }

  @Nullable
  private EditorComposite getLastSelected() {
    final EditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
    if (currentWindow != null) {
      return currentWindow.getSelectedEditor();
    }
    return null;
  }

  /**
   * @param splitters - taken getAllSplitters() value if parameter is null
   */
  void runChange(@NotNull FileEditorManagerChange change, @Nullable EditorsSplitters splitters) {
    Set<EditorsSplitters> target = new HashSet<>();
    if (splitters == null) {
      target.addAll(getAllSplitters());
    }
    else {
      target.add(splitters);
    }

    for (EditorsSplitters each : target) {
      each.myInsideChange++;
      try {
        change.run(each);
      }
      finally {
        each.myInsideChange--;
      }
    }
  }

  //================== Listeners =====================

  /**
   * Closes deleted files. Closes file which are in the deleted directories.
   */
  private final class MyVirtualFileListener implements VirtualFileListener {
    @Override
    public void beforeFileDeletion(@NotNull VirtualFileEvent e) {
      assertDispatchThread();

      boolean moveFocus = moveFocusOnDelete();

      final VirtualFile file = e.getFile();
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        if (VfsUtilCore.isAncestor(file, openFiles[i], false)) {
          closeFile(openFiles[i], moveFocus, true);
        }
      }
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent e) {
      if (VirtualFile.PROP_NAME.equals(e.getPropertyName())) {
        assertDispatchThread();
        final VirtualFile file = e.getFile();
        if (isFileOpen(file)) {
          updateFileName(file);
          updateFileIcon(file); // file type can change after renaming
          updateFileBackgroundColor(file);
        }
      }
      else if (VirtualFile.PROP_WRITABLE.equals(e.getPropertyName()) || VirtualFile.PROP_ENCODING.equals(e.getPropertyName())) {
        // TODO: message bus?
        updateIconAndStatusBar(e);
      }
    }

    private void updateIconAndStatusBar(final VirtualFilePropertyEvent e) {
      assertDispatchThread();
      final VirtualFile file = e.getFile();
      if (isFileOpen(file)) {
        updateFileIcon(file);
        if (file.equals(getSelectedFiles()[0])) { // update "write" status
          final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
          assert statusBar != null;
          statusBar.updateWidgets();
        }
      }
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent e) {
      final VirtualFile file = e.getFile();
      final VirtualFile[] openFiles = getOpenFiles();
      for (final VirtualFile openFile : openFiles) {
        if (VfsUtilCore.isAncestor(file, openFile, false)) {
          updateFileName(openFile);
          updateFileBackgroundColor(openFile);
        }
      }
    }
  }

  private static boolean moveFocusOnDelete() {
    final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (window != null) {
      final Component component = FocusTrackback.getFocusFor(window);
      if (component != null) {
        return component instanceof EditorComponentImpl;
      }
      return window instanceof IdeFrameImpl;
    }
    return true;
  }

  @Override
  public boolean isInsideChange() {
    return getSplitters().isInsideChange();
  }

  private final class MyEditorPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(@NotNull final PropertyChangeEvent e) {
      assertDispatchThread();

      final String propertyName = e.getPropertyName();
      if (FileEditor.PROP_MODIFIED.equals(propertyName)) {
        final FileEditor editor = (FileEditor)e.getSource();
        final EditorComposite composite = getEditorComposite(editor);
        if (composite != null) {
          updateFileIcon(composite.getFile());
        }
      }
      else if (FileEditor.PROP_VALID.equals(propertyName)) {
        final boolean valid = ((Boolean)e.getNewValue()).booleanValue();
        if (!valid) {
          final FileEditor editor = (FileEditor)e.getSource();
          LOG.assertTrue(editor != null);
          final EditorComposite composite = getEditorComposite(editor);
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
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
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
    public void fileStatusChanged(@NotNull final VirtualFile file) { // update color of the file (if necessary)
      assertDispatchThread();
      if (isFileOpen(file)) {
        updateFileStatus(file);
      }
    }

    private void updateFileStatus(final VirtualFile file) {
      updateFileColor(file);
      updateFileIcon(file);
    }
  }

  /**
   * Gets events from FileTypeManager and updates icons on tabs
   */
  private final class MyFileTypeListener implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull final FileTypeEvent event) {
      assertDispatchThread();
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        updateFileIcon(file);
      }
    }
  }

  private class MyRootsListener implements ModuleRootListener {
    private boolean myScheduled;

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      if (myScheduled) return;
      myScheduled = true;
      DumbService.getInstance(myProject).runWhenSmart(() -> {
        myScheduled = false;
        handleRootChange();
      });
    }

    private void handleRootChange() {
      EditorFileSwapper[] swappers = Extensions.getExtensions(EditorFileSwapper.EP_NAME);

      for (EditorWindow eachWindow : getWindows()) {
        EditorWithProviderComposite selected = eachWindow.getSelectedEditor();
        EditorWithProviderComposite[] editors = eachWindow.getEditors();
        for (int i = 0; i < editors.length; i++) {
          EditorWithProviderComposite editor = editors[i];
          VirtualFile file = editor.getFile();
          if (!file.isValid()) continue;

          Pair<VirtualFile, Integer> newFilePair = null;

          for (EditorFileSwapper each : swappers) {
            newFilePair = each.getFileToSwapTo(myProject, editor);
            if (newFilePair != null) break;
          }

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
    public void uiSettingsChanged(final UISettings uiSettings) {
      assertDispatchThread();
      setTabsMode(uiSettings.getEditorTabPlacement() != UISettings.TABS_NONE && !uiSettings.getPresentationMode());

      for (EditorsSplitters each : getAllSplitters()) {
        each.setTabsPlacement(uiSettings.getEditorTabPlacement());
        each.trimToSize(uiSettings.getEditorTabLimit());

        // Tab layout policy
        if (uiSettings.getScrollTabLayoutInEditor()) {
          each.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        }
        else {
          each.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        }
      }

      // "Mark modified files with asterisk"
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        updateFileIcon(file);
        updateFileName(file);
        updateFileBackgroundColor(file);
      }
    }
  }

  @Override
  public void closeAllFiles() {
    final VirtualFile[] openFiles = getSplitters().getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      closeFile(openFile);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getSiblings(@NotNull VirtualFile file) {
    return getOpenFiles();
  }

  void queueUpdateFile(@NotNull final VirtualFile file) {
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

  @NotNull
  public List<Pair<VirtualFile, EditorWindow>> getSelectionHistory() {
    List<Pair<VirtualFile, EditorWindow>> copy = new ArrayList<>();
    for (Pair<VirtualFile, EditorWindow> pair : mySelectionHistory) {
      if (pair.second.getFiles().length == 0) {
        final EditorWindow[] windows = pair.second.getOwner().getWindows();
        if (windows.length > 0 && windows[0] != null && windows[0].getFiles().length > 0) {
          final Pair<VirtualFile, EditorWindow> p = Pair.create(pair.first, windows[0]);
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
    final Pair<VirtualFile, EditorWindow> record = Pair.create(file, window);
    mySelectionHistory.remove(record);
    mySelectionHistory.add(0, record);
  }

  void removeSelectionRecord(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    mySelectionHistory.remove(Pair.create(file, window));
    updateFileName(file);
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    return myBusyObject.getReady(requestor);
  }
}
