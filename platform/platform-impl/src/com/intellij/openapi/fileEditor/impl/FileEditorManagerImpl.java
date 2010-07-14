/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.impl.MessageListenerList;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public class FileEditorManagerImpl extends FileEditorManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl");
  private static final Key<LocalFileSystem.WatchRequest> WATCH_REQUEST_KEY = Key.create("WATCH_REQUEST_KEY");
  private static final Key<Boolean> DUMB_AWARE = Key.create("DUMB_AWARE");

  private static final FileEditor[] EMPTY_EDITOR_ARRAY = {};
  private static final FileEditorProvider[] EMPTY_PROVIDER_ARRAY = {};

  private volatile JPanel myPanels;
  private EditorsSplitters mySplitters;
  private final Project myProject;

  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("FileEditorManagerUpdateQueue", 50, true, null);

  /**
   * Removes invalid myEditor and updates "modified" status.
   */
  private final MyEditorPropertyChangeListener myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();

  private final List<EditorDataProvider> myDataProviders = new ArrayList<EditorDataProvider>();

  public FileEditorManagerImpl(final Project project) {
/*    ApplicationManager.getApplication().assertIsDispatchThread(); */
    myProject = project;
    myListenerList = new MessageListenerList<FileEditorManagerListener>(myProject.getMessageBus(), FileEditorManagerListener.FILE_EDITOR_MANAGER);
  }

  public static boolean isDumbAware(FileEditor editor) {
    return Boolean.TRUE.equals(editor.getUserData(DUMB_AWARE));
  }

  //-------------------------------------------------------------------------------

  public JComponent getComponent() {
    initUI();
    return myPanels;
  }

  public EditorsSplitters getSplitters() {
    initUI();
    return mySplitters;
  }

  private final Object myInitLock = new Object();
  private void initUI() {
    if (myPanels == null) {
      synchronized (myInitLock) {
        if (myPanels == null) {
          myPanels = new JPanel(new BorderLayout());
          mySplitters = new EditorsSplitters(this);
          myPanels.add(mySplitters, BorderLayout.CENTER);
        }
      }
    }
  }

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
   * @return color of the <code>file</code> which corresponds to the
   *         file's status
   */
  public Color getFileColor(@NotNull final VirtualFile file) {
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    Color statusColor = fileStatusManager != null ? fileStatusManager.getStatus(file).getColor() : Color.BLACK;
    if (statusColor == null) statusColor = Color.BLACK;
    return statusColor;
  }

  public boolean isProblem(@NotNull final VirtualFile file) {
    return false;
  }

  public String getFileTooltipText(VirtualFile file) {
    return file.getPresentableUrl();
  }

  public void updateFilePresentation(VirtualFile file) {
    if (!isFileOpen(file)) return;

    updateFileColor(file);
    updateFileIcon(file);
    updateFileName(file);
    updateFileBackgroundColor(file);
  }

  /**
   * Updates tab color for the specified <code>file</code>. The <code>file</code>
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  private void updateFileColor(final VirtualFile file) {
    getSplitters().updateFileColor(file);
  }

  private void updateFileBackgroundColor(final VirtualFile file) {
    getSplitters().updateFileBackgroundColor(file);
  }

  /**
   * Updates tab icon for the specified <code>file</code>. The <code>file</code>
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  protected void updateFileIcon(final VirtualFile file) {
    getSplitters().updateFileIcon(file);
  }

  /**
   * Updates tab title and tab tool tip for the specified <code>file</code>
   */
  void updateFileName(@Nullable final VirtualFile file) {
    // Queue here is to prevent title flickering when tab is being closed and two events arriving: with component==null and component==next focused tab
    // only the last event makes sense to handle
    myQueue.queue(new Update("UpdateFileName "+(file==null?"":file.getPath())) {
      public boolean isExpired() {
        return myProject.isDisposed() || !myProject.isOpen() || (file == null ? super.isExpired() : !file.isValid());
      }

      public void run() {
        final WindowManagerEx windowManagerEx = WindowManagerEx.getInstanceEx();
        final IdeFrameImpl frame = windowManagerEx.getFrame(myProject);
        LOG.assertTrue(frame != null);
        getSplitters().updateFileName(file);
        File ioFile = file == null ? null : new File(file.getPresentableUrl());
        frame.setFileTitle(file == null ? null : FrameTitleBuilder.getInstance().getFileTitle(myProject, file), ioFile);
      }
    });
  }

  //-------------------------------------------------------


  public VirtualFile getFile(@NotNull final FileEditor editor) {
    final EditorComposite editorComposite = getEditorComposite(editor);
    if (editorComposite != null) {
      return editorComposite.getFile();
    }
    return null;
  }

  public void unsplitWindow() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplit(true);
    }
  }

  public void unsplitAllWindow() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplitAll();
    }
  }

  @Override
  public int getWindowSplitCount() {
    return getSplitters().getSplitCount();
  }

  @NotNull
  public EditorWindow[] getWindows() {
    return getSplitters().getWindows();
  }

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

  public void changeSplitterOrientation() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.changeOrientation();
    }
  }


  public void flipTabs() {
    /*
    if (myTabs == null) {
      myTabs = new EditorTabs (this, UISettings.getInstance().EDITOR_TAB_PLACEMENT);
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

  public boolean tabsMode() {
    return false;
  }

  private void setTabsMode(final boolean mode) {
    if (tabsMode() != mode) {
      flipTabs();
    }
    //LOG.assertTrue (tabsMode () == mode);
  }


  public boolean isInSplitter() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    return currentWindow != null && currentWindow.inSplitter();
  }

  public boolean hasOpenedFile() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    return currentWindow != null && currentWindow.getSelectedEditor() != null;
  }

  public VirtualFile getCurrentFile() {
    return getSplitters().getCurrentFile();
  }

  public EditorWindow getCurrentWindow() {
    return getSplitters().getCurrentWindow();
  }

  public void setCurrentWindow(final EditorWindow window) {
    getSplitters().setCurrentWindow(window, true);
  }

  public void closeFile(@NotNull final VirtualFile file, @NotNull final EditorWindow window) {
    assertDispatchThread();

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        if (window.isFileOpen(file)) {
          window.closeFile(file);
          final List<EditorWindow> windows = getSplitters().findWindows(file);
          if (windows.isEmpty()) { // no more windows containing this file left
            final LocalFileSystem.WatchRequest request = file.getUserData(WATCH_REQUEST_KEY);
            if (request != null) {
              LocalFileSystem.getInstance().removeWatchedRoot(request);
            }
          }
        }
      }
    }, IdeBundle.message("command.close.active.editor"), null);
  }

  //============================= EditorManager methods ================================

  public void closeFile(@NotNull final VirtualFile file) {
    closeFile(file, true);
  }

  public void closeFile(@NotNull final VirtualFile file, final boolean moveFocus) {
    assertDispatchThread();

    final LocalFileSystem.WatchRequest request = file.getUserData(WATCH_REQUEST_KEY);
    if (request != null) {
      LocalFileSystem.getInstance().removeWatchedRoot(request);
    }

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        closeFileImpl(file, moveFocus);
      }
    }, "", null);
  }


  private VirtualFile findNextFile(final VirtualFile file) {
    final EditorWindow [] windows = getWindows(); // TODO: use current file as base
    for (int i = 0; i != windows.length; ++ i) {
      final VirtualFile[] files = windows[i].getFiles();
      for (final VirtualFile fileAt : files) {
        if (fileAt != file) {
          return fileAt;
        }
      }
    }
    return null;
  }

  private void closeFileImpl(@NotNull final VirtualFile file, final boolean moveFocus) {
    assertDispatchThread();
    getSplitters().runChange(new Runnable() {
      public void run() {
        final List<EditorWindow> windows = getSplitters().findWindows(file);
        if (!windows.isEmpty()) {
          final VirtualFile nextFile = findNextFile(file);
          for (final EditorWindow window : windows) {
            LOG.assertTrue(window.getSelectedEditor() != null);
            window.closeFile(file, false, moveFocus);
            if (window.getTabCount() == 0 && nextFile != null) {
              EditorWithProviderComposite newComposite = newEditorComposite(nextFile);
              window.setEditor(newComposite, moveFocus); // newComposite can be null
            }
          }
          // cleanup windows with no tabs
          for (final EditorWindow window : windows) {
            if (window.isDisposed()) {
              // call to window.unsplit() which might make its sibling disposed
              continue;
            }
            if (window.getTabCount() == 0) {
              window.unsplit(false);
            }
          }
        }
      }
    });
  }

//-------------------------------------- Open File ----------------------------------------

  @NotNull public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull final VirtualFile file, final boolean focusEditor) {
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertDispatchThread();
    return openFileImpl2(getSplitters().getOrCreateCurrentWindow(file), file, focusEditor, null);
  }

  @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(final EditorWindow window, final VirtualFile file, final boolean focusEditor,
                                                                  final HistoryEntry entry) {
    final Ref<Pair<FileEditor[], FileEditorProvider[]>> resHolder = new Ref<Pair<FileEditor[], FileEditorProvider[]>>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        resHolder.set(openFileImpl3(window, file, focusEditor, entry, true));
      }
    }, "", null);
    return resHolder.get();
  }

  /**
   * @param file  to be opened. Unlike openFile method, file can be
   *              invalid. For example, all file were invalidate and they are being
   *              removed one by one. If we have removed one invalid file, then another
   *              invalid file become selected. That's why we do not require that
   *              passed file is valid.
   * @param entry map between FileEditorProvider and FileEditorState. If this parameter
   * @param current
   */
  @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(final EditorWindow window,
                                                                  @NotNull final VirtualFile file,
                                                                  final boolean focusEditor,
                                                                  final HistoryEntry entry,
                                                                  boolean current) {
    // Open file
    FileEditor[] editors;
    FileEditorProvider[] providers;
    final EditorWithProviderComposite newSelectedComposite;
    boolean newEditorCreated = false;

    final boolean open = window.isFileOpen(file);
    if (open) {
      // File is already opened. In this case we have to just select existing EditorComposite
      newSelectedComposite = window.findFileComposite(file);
      LOG.assertTrue(newSelectedComposite != null);

      editors = newSelectedComposite.getEditors();
      providers = newSelectedComposite.getProviders();
    }
    else {
      // File is not opened yet. In this case we have to create editors
      // and select the created EditorComposite.
      final FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
      providers = editorProviderManager.getProviders(myProject, file);
      if (DumbService.getInstance(myProject).isDumb()) {
        final List<FileEditorProvider> dumbAware = ContainerUtil.findAll(providers, new Condition<FileEditorProvider>() {
          public boolean value(FileEditorProvider fileEditorProvider) {
            return DumbService.isDumbAware(fileEditorProvider);
          }
        });
        providers = dumbAware.toArray(new FileEditorProvider[dumbAware.size()]);
      }

      if (providers.length == 0) {
        return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
      }
      newEditorCreated = true;

      getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileOpened(this, file);

      editors = new FileEditor[providers.length];
      for (int i = 0; i < providers.length; i++) {
        try {
          final FileEditorProvider provider = providers[i];
          LOG.assertTrue(provider != null);
          LOG.assertTrue(provider.accept(myProject, file));
          final FileEditor editor = provider.createEditor(myProject, file);
          LOG.assertTrue(editor != null);
          LOG.assertTrue(editor.isValid());
          editors[i] = editor;
          // Register PropertyChangeListener into editor
          editor.addPropertyChangeListener(myEditorPropertyChangeListener);
          editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(provider));

          if (current && editor instanceof TextEditorImpl) {
            ((TextEditorImpl)editor).initFolding();
          }
       }
        catch (Exception e) {
          LOG.error(e);
        }
        catch (AssertionError e) {
          LOG.error(e);
        }
      }

      // Now we have to create EditorComposite and insert it into the TabbedEditorComponent.
      // After that we have to select opened editor.
      newSelectedComposite = new EditorWithProviderComposite(file, editors, providers, this);
    }

    window.setEditor(newSelectedComposite, focusEditor);

    final EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(myProject);
    for (int i = 0; i < editors.length; i++) {
      final FileEditor editor = editors[i];
      if (editor instanceof TextEditor) {
        // hack!!!
        // This code prevents "jumping" on next repaint.
        ((EditorEx)((TextEditor)editor).getEditor()).stopOptimizedScrolling();
      }

      final FileEditorProvider provider = providers[i];//getProvider(editor);

      // Restore editor state
      FileEditorState state = null;
      if (entry != null) {
        state = entry.getState(provider);
      }
      if (state == null && !open) {
        // We have to try to get state from the history only in case
        // if editor is not opened. Otherwise history enty might have a state
        // out of sync with the current editor state.
        state = editorHistoryManager.getState(file, provider);
      }
      if (state != null) {
        editor.setState(state);
      }
    }

    // Restore selected editor
    final FileEditorProvider selectedProvider = editorHistoryManager.getSelectedProvider(file);
    if (selectedProvider != null) {
      final FileEditor[] _editors = newSelectedComposite.getEditors();
      final FileEditorProvider[] _providers = newSelectedComposite.getProviders();
      for (int i = _editors.length - 1; i >= 0; i--) {
        final FileEditorProvider provider = _providers[i];//getProvider(_editors[i]);
        if (provider.equals(selectedProvider)) {
          newSelectedComposite.setSelectedEditor(i);
          break;
        }
      }
    }

    // Notify editors about selection changes
    getSplitters().setCurrentWindow(window, false);
    newSelectedComposite.getSelectedEditor().selectNotify();

    if (newEditorCreated) {
      getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(this, file);

      //Add request to watch this editor's virtual file
      final VirtualFile parentDir = file.getParent();
      if (parentDir != null) {
        final LocalFileSystem.WatchRequest request = LocalFileSystem.getInstance().addRootToWatch(parentDir.getPath(), false);
        file.putUserData(WATCH_REQUEST_KEY, request);
      }
    }

    //[jeka] this is a hack to support back-forward navigation
    // previously here was incorrect call to fireSelectionChanged() with a side-effect
    ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();

    // Transfer focus into editor
    if (!ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
      if (focusEditor) {
        //myFirstIsActive = myTabbedContainer1.equals(tabbedContainer);
        window.setAsCurrentWindow(false);
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
      }
    }

    // Update frame and tab title
    updateFileName(file);

    // Make back/forward work
    IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();

    return Pair.create(editors, providers);
  }

  private void setSelectedEditor(VirtualFile file, String fileEditorProviderId) {
    EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite == null) {
      final List<EditorWithProviderComposite> composites = getEditorComposites(file);

      if (composites.isEmpty()) return;
      composite = composites.get(0);
    }

    final FileEditorProvider[] editorProviders = composite.getProviders();
    final FileEditorProvider selectedProvider = composite.getSelectedEditorWithProvider().getSecond();

    for (int i = 0; i < editorProviders.length; i++) {
      if (editorProviders[i].getEditorTypeId().equals(fileEditorProviderId) &&  !selectedProvider.equals(editorProviders[i])) {
        composite.setSelectedEditor(i);
        composite.getSelectedEditor().selectNotify();
      }
    }
  }


  private EditorWithProviderComposite newEditorComposite(final VirtualFile file) {
    if (file == null) {
      return null;
    }

    final FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    final FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
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
      if (editor instanceof TextEditor) {
        // hack!!!
        // This code prevents "jumping" on next repaint.
        //((EditorEx)((TextEditor)editor).getEditor()).stopOptimizedScrolling();
      }

      final FileEditorProvider provider = providers[i];

// Restore myEditor state
      FileEditorState state = editorHistoryManager.getState(file, provider);
      if (state != null) {
        editor.setState(state);
      }
    }
    return newComposite;
  }

  @NotNull
  public List<FileEditor> openEditor(@NotNull final OpenFileDescriptor descriptor, final boolean focusEditor) {
    assertDispatchThread();
    if (descriptor.getFile() instanceof VirtualFileWindow) {
      VirtualFileWindow delegate = (VirtualFileWindow)descriptor.getFile();
      int hostOffset = delegate.getDocumentWindow().injectedToHost(descriptor.getOffset());
      OpenFileDescriptor realDescriptor = new OpenFileDescriptor(descriptor.getProject(), delegate.getDelegate(), hostOffset);
      return openEditor(realDescriptor, focusEditor);
    }

    final List<FileEditor> result = new ArrayList<FileEditor>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        VirtualFile file = descriptor.getFile();
        final FileEditor[] editors = openFile(file, focusEditor);
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
      }
    }, "", null);

    return result;
  }

  private boolean navigateAndSelectEditor(final NavigatableFileEditor editor, final OpenFileDescriptor descriptor) {
    if (editor.canNavigateTo(descriptor)) {
      setSelectedEditor(editor);
      editor.navigateTo(descriptor);
      return true;
    }

    return false;
  }

  private void setSelectedEditor(final FileEditor editor) {
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

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void registerExtraEditorDataProvider(@NotNull final EditorDataProvider provider, Disposable parentDisposable) {
    myDataProviders.add(provider);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          myDataProviders.remove(provider);
        }
      });
    }
  }

  @Nullable
  public final Object getData(String dataId, Editor editor, final VirtualFile file) {
    for (final EditorDataProvider dataProvider : myDataProviders) {
      final Object o = dataProvider.getData(dataId, editor, file);
      if (o != null) return o;
    }
    return null;
  }

  @Nullable
  public Editor openTextEditor(final OpenFileDescriptor descriptor, final boolean focusEditor) {
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

  protected Editor getOpenedEditor(final Editor editor, final boolean focusEditor) {
    return editor;
  }

  public Editor getSelectedTextEditor() {
    assertReadAccess();

    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      final EditorWithProviderComposite selectedEditor = currentWindow.getSelectedEditor();
      if (selectedEditor != null && selectedEditor.getSelectedEditor() instanceof TextEditor) {
        return ((TextEditor)selectedEditor.getSelectedEditor()).getEditor();
      }
    }

    return null;
  }


  public boolean isFileOpen(@NotNull final VirtualFile file) {
    return getEditors(file).length != 0;
  }

  @NotNull
  public VirtualFile[] getOpenFiles() {
    return getSplitters().getOpenFiles();
  }

  @NotNull
  public VirtualFile[] getSelectedFiles() {
    return getSplitters().getSelectedFiles();
  }

  @NotNull
  public FileEditor[] getSelectedEditors() {
    return getSplitters().getSelectedEditors();
  }

  public FileEditor getSelectedEditor(@NotNull final VirtualFile file) {
    final Pair<FileEditor, FileEditorProvider> selectedEditorWithProvider = getSelectedEditorWithProvider(file);
    return selectedEditorWithProvider == null ? null : selectedEditorWithProvider.getFirst();
  }


  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    final EditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
    if (composite != null) {
      return composite.getSelectedEditorWithProvider();
    }

    final List<EditorWithProviderComposite> composites = getEditorComposites(file);
    return composites.isEmpty() ? null : composites.get(0).getSelectedEditorWithProvider();
  }

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
    else {
      return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
    }
  }

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
    else {
      return EMPTY_EDITOR_ARRAY;
    }
  }

  @NotNull
  @Override
  public FileEditor[] getAllEditors(@NotNull VirtualFile file) {
    List<EditorWithProviderComposite> editorComposites = getEditorComposites(file);
    List<FileEditor> editors = new ArrayList<FileEditor>();
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
  public List<EditorWithProviderComposite> getEditorComposites(final VirtualFile file) {
    return getSplitters().findEditorComposites(file);
  }

  @NotNull
  public FileEditor[] getAllEditors() {
    assertReadAccess();
    final ArrayList<FileEditor> result = new ArrayList<FileEditor>();
    final EditorWithProviderComposite[] editorsComposites = getSplitters().getEditorsComposites();
    for (EditorWithProviderComposite editorsComposite : editorsComposites) {
      final FileEditor[] editors = editorsComposite.getEditors();
      ContainerUtil.addAll(result, editors);
    }
    return result.toArray(new FileEditor[result.size()]);
  }

  public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
    addTopComponent(editor, annotationComponent);
  }

  public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
    removeTopComponent(editor, annotationComponent);
  }

  public void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.addTopComponent(editor, component);
    }
  }

  public void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.removeTopComponent(editor, component);
    }
  }

  public void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.addBottomComponent(editor, component);
    }
  }

  public void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    final EditorComposite composite = getEditorComposite(editor);
    if (composite != null) {
      composite.removeBottomComponent(editor, component);
    }
  }

  private final MessageListenerList<FileEditorManagerListener> myListenerList;

  public void addFileEditorManagerListener(@NotNull final FileEditorManagerListener listener) {
    myListenerList.add(listener);
  }

  public void addFileEditorManagerListener(@NotNull final FileEditorManagerListener listener, final Disposable parentDisposable) {
    myListenerList.add(listener, parentDisposable);
  }

  public void removeFileEditorManagerListener(@NotNull final FileEditorManagerListener listener) {
    myListenerList.remove(listener);
  }

// ProjectComponent methods

  public void projectOpened() {
    //myFocusWatcher.install(myWindows.getComponent ());
    getSplitters().startListeningFocus();

    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);

    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      /**
       * Updates tabs colors
       */
      final MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
      fileStatusManager.addFileStatusListener(myFileStatusListener, myProject);
    }
    connection.subscribe(AppTopics.FILE_TYPES, new MyFileTypeListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootsListener());

    /**
     * Updates tabs names
     */
    final MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener, myProject);
    /**
     * Extends/cuts number of opened tabs. Also updates location of tabs.
     */
    final MyUISettingsListener myUISettingsListener = new MyUISettingsListener();
    UISettings.getInstance().addUISettingsListener(myUISettingsListener, myProject);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              public void run() {
                setTabsMode(UISettings.getInstance().EDITOR_TAB_PLACEMENT != UISettings.TABS_NONE);
                getSplitters().openFiles();
                LaterInvocator.invokeLater(new Runnable() {
                  public void run() {
                    long currentTime = System.nanoTime();
                    Long startTime = myProject.getUserData(ProjectImpl.CREATION_TIME);
                    if (startTime != null) {
                      LOG.info("Project opening took " + (currentTime - startTime.longValue()) / 1000000 + " ms");
                      PluginManager.dumpPluginClassStatistics();
                    }
                  }
                });
// group 1
              }
            }, "", null);
          }
        });
      }
    });
  }

  public void projectClosed() {
    //myFocusWatcher.deinstall(myWindows.getComponent ());
    getSplitters().dispose();

// Dispose created editors. We do not use use closeEditor method because
// it fires event and changes history.
    closeAllFiles();
  }

// BaseCompomemnt methods

  @NotNull
  public String getComponentName() {
    return "FileEditorManager";
  }

  public void initComponent() { /* really do nothing */ }

  public void disposeComponent() { /* really do nothing */  }

//JDOMExternalizable methods

  public void writeExternal(final Element element) {
    getSplitters().writeExternal(element);
  }

  public void readExternal(final Element element) {
    getSplitters().readExternal(element);
  }

  private EditorWithProviderComposite getEditorComposite(@NotNull final FileEditor editor) {
    final EditorWithProviderComposite[] editorsComposites = getSplitters().getEditorsComposites();
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
    return null;
  }

//======================= Misc =====================

  private static void assertDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }
  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  public void fireSelectionChanged(final EditorComposite oldSelectedComposite, final EditorComposite newSelectedComposite) {
    final VirtualFile oldSelectedFile = oldSelectedComposite != null ? oldSelectedComposite.getFile() : null;
    final VirtualFile newSelectedFile = newSelectedComposite != null ? newSelectedComposite.getFile() : null;

    final FileEditor oldSelectedEditor = oldSelectedComposite != null && !oldSelectedComposite.isDisposed() ? oldSelectedComposite.getSelectedEditor() : null;
    final FileEditor newSelectedEditor = newSelectedComposite != null && !newSelectedComposite.isDisposed() ? newSelectedComposite.getSelectedEditor() : null;

    final boolean filesEqual = oldSelectedFile == null ? newSelectedFile == null : oldSelectedFile.equals(newSelectedFile);
    final boolean editorsEqual = oldSelectedEditor == null ? newSelectedEditor == null : oldSelectedEditor.equals(newSelectedEditor);
    if (!filesEqual || !editorsEqual) {
      final FileEditorManagerEvent event =
        new FileEditorManagerEvent(this, oldSelectedFile, oldSelectedEditor, newSelectedFile, newSelectedEditor);
      final FileEditorManagerListener publisher = getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
      publisher.selectionChanged(event);
    }
  }

  public boolean isChanged(@NotNull final EditorComposite editor) {
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      if (!fileStatusManager.getStatus(editor.getFile()).equals(FileStatus.NOT_CHANGED)) {
        return true;
      }
    }
    return false;
  }

  public void disposeComposite(EditorWithProviderComposite editor) {
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

  EditorComposite getLastSelected() {
    final EditorWindow currentWindow = getSplitters().getCurrentWindow();
    if (currentWindow != null) {
      return currentWindow.getSelectedEditor();
    }
    return null;
  }

  public void runChange(Runnable runnable) {
    getSplitters().runChange(runnable);
  }

  //================== Listeners =====================

  /**
   * Closes deleted files. Closes file which are in the deleted directories.
   */
  private final class MyVirtualFileListener extends VirtualFileAdapter {
    public void beforeFileDeletion(VirtualFileEvent e) {
      assertDispatchThread();
      final VirtualFile file = e.getFile();
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        if (VfsUtil.isAncestor(file, openFiles[i], false)) {
          closeFile(openFiles[i]);
        }
      }
    }

    public void propertyChanged(VirtualFilePropertyEvent e) {
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
        updateIconAndStatusbar(e);
      }
    }

    private void updateIconAndStatusbar(final VirtualFilePropertyEvent e) {
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

    public void fileMoved(VirtualFileMoveEvent e) {
      final VirtualFile file = e.getFile();
      final VirtualFile[] openFiles = getOpenFiles();
      for (final VirtualFile openFile : openFiles) {
        if (VfsUtil.isAncestor(file, openFile, false)) {
          updateFileName(openFile);
          updateFileBackgroundColor(openFile);
        }
      }
    }
  }

/*
private final class MyVirtualFileListener extends VirtualFileAdapter {
  public void beforeFileDeletion(final VirtualFileEvent e) {
    assertDispatchThread();
    final VirtualFile file = e.getFile();
    final VirtualFile[] openFiles = getOpenFiles();
    for (int i = openFiles.length - 1; i >= 0; i--) {
      if (VfsUtil.isAncestor(file, openFiles[i], false)) {
        closeFile(openFiles[i]);
      }
    }
  }

  public void propertyChanged(final VirtualFilePropertyEvent e) {
    if (VirtualFile.PROP_WRITABLE.equals(e.getPropertyName())) {
      assertDispatchThread();
      final VirtualFile file = e.getFile();
      if (isFileOpen(file)) {
        if (file.equals(getSelectedFiles()[0])) { // update "write" status
          final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
          LOG.assertTrue(statusBar != null);
          statusBar.setWriteStatus(!file.isWritable());
        }
      }
    }
  }

  //public void fileMoved(final VirtualFileMoveEvent e){ }
}
*/

  public boolean isInsideChange() {
    return getSplitters().isInsideChange();
  }

  private final class MyEditorPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(final PropertyChangeEvent e) {
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
    public void fileStatusesChanged() { // update color of all open files
      assertDispatchThread();
      LOG.debug("FileEditorManagerImpl.MyFileStatusListener.fileStatusesChanged()");
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (LOG.isDebugEnabled()) {
              LOG.debug("updating file status in tab for " + file.getPath());
            }
            updateFileStatus(file);
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    }

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
    public void beforeFileTypesChanged(FileTypeEvent event) {
    }

    public void fileTypesChanged(final FileTypeEvent event) {
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
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      EditorFileSwapper[] swappers = Extensions.getExtensions(EditorFileSwapper.EP_NAME);

      for (EditorWindow eachWindow : getWindows()) {
        VirtualFile selected = eachWindow.getSelectedFile();
        VirtualFile[] files = eachWindow.getFiles();
        for (int i = 0; i < files.length - 1 + 1; i++) {
          VirtualFile eachFile = files[i];
          VirtualFile newFile = null;
          for (EditorFileSwapper each : swappers) {
            newFile = each.getFileToSwapTo(myProject, eachFile);
          }
          if (newFile == null) continue;

          // already open
          if (eachWindow.findFileIndex(newFile) != -1) continue;

          closeFile(eachFile, eachWindow);
          try {
            newFile.putUserData(EditorWindow.INITIAL_INDEX_KEY, i);
            openFile(newFile, eachFile == selected);
          }
          finally {
            newFile.putUserData(EditorWindow.INITIAL_INDEX_KEY, null);
          }
        }
      }
    }
  }

  /**
   * Gets notifications from UISetting component to track changes of RECENT_FILES_LIMIT
   * and EDITOR_TAB_LIMIT, etc values.
   */
  private final class MyUISettingsListener implements UISettingsListener {
    public void uiSettingsChanged(final UISettings source) {
      assertDispatchThread();
      setTabsMode(source.EDITOR_TAB_PLACEMENT != UISettings.TABS_NONE);
      getSplitters().setTabsPlacement(source.EDITOR_TAB_PLACEMENT);
      getSplitters().trimToSize(source.EDITOR_TAB_LIMIT);

      // Tab layout policy
      if (source.SCROLL_TAB_LAYOUT_IN_EDITOR) {
        getSplitters().setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
      }
      else {
        getSplitters().setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
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

  public void closeAllFiles() {
    final VirtualFile[] openFiles = getSplitters().getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      closeFile(openFile);
    }
  }

  @NotNull
  public VirtualFile[] getSiblings(VirtualFile file) {
    return getOpenFiles();
  }

  protected void queueUpdateFile(final VirtualFile file) {
    myQueue.queue(new Update(file) {
      public void run() {
        if (isFileOpen(file)) {
          updateFileIcon(file);
          updateFileColor(file);
          updateFileBackgroundColor(file);
        }

      }
    });
  }
}
