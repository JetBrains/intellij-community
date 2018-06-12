// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

@State(name = "editorHistoryManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class EditorHistoryManager implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(EditorHistoryManager.class);

  private final Project myProject;

  public static EditorHistoryManager getInstance(@NotNull Project project){
    return ServiceManager.getService(project, EditorHistoryManager.class);
  }

  /**
   * State corresponding to the most recent file is the last
   */
  private final List<HistoryEntry> myEntriesList = new ArrayList<>();

  EditorHistoryManager(@NotNull Project project) {
    myProject = project;

    MessageBusConnection connection = project.getMessageBus().connect();

    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> trimToSize());

    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new FileEditorManagerListener.Before() {
      @Override
      public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateHistoryEntry(file, false);
      }
    });
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorManagerListener());
  }

  static class EditorHistoryManagerStartUpActivity implements DumbAware, StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstance(project);
    }
  }

  private synchronized void removeEntry(@NotNull HistoryEntry entry) {
    if (myEntriesList.remove(entry)) {
      entry.destroy();
    }
  }

  private synchronized void moveOnTop(@NotNull HistoryEntry entry) {
    myEntriesList.remove(entry);
    myEntriesList.add(entry);
  }

  /**
   * Makes file most recent one
   */
  private void fileOpenedImpl(@NotNull VirtualFile file, @Nullable FileEditor fallbackEditor, @Nullable FileEditorProvider fallbackProvider) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    // don't add files that cannot be found via VFM (light & etc.)
    if (VirtualFileManager.getInstance().findFileByUrl(file.getUrl()) == null) {
      return;
    }

    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(myProject);

    final Pair<FileEditor[], FileEditorProvider[]> editorsWithProviders = editorManager.getEditorsWithProviders(file);
    FileEditor[] editors = editorsWithProviders.getFirst();
    FileEditorProvider[] oldProviders = editorsWithProviders.getSecond();
    LOG.assertTrue(editors.length == oldProviders.length, "Different number of editors and providers");
    if (editors.length <= 0 && fallbackEditor != null && fallbackProvider != null) {
      editors = new FileEditor[] { fallbackEditor };
      oldProviders = new FileEditorProvider[] { fallbackProvider };
    }
    if (editors.length <= 0) {
      LOG.error("No editors for file " + file.getPresentableUrl());
    }
    FileEditor selectedEditor = editorManager.getSelectedEditor(file);
    if (selectedEditor == null) {
      selectedEditor = fallbackEditor;
    }
    LOG.assertTrue(selectedEditor != null);
    final int selectedProviderIndex = ArrayUtilRt.find(editors, selectedEditor);
    LOG.assertTrue(selectedProviderIndex != -1, "Can't find " + selectedEditor + " among " + Arrays.asList(editors));

    final HistoryEntry entry = getEntry(file);
    if (entry != null) {
      moveOnTop(entry);
    }
    else {
      final FileEditorState[] states = new FileEditorState[editors.length];
      final FileEditorProvider[] providers = new FileEditorProvider[editors.length];
      for (int i = states.length - 1; i >= 0; i--) {
        final FileEditorProvider provider = oldProviders[i];
        LOG.assertTrue(provider != null);
        providers[i] = provider;
        FileEditor editor = editors[i];
        if (editor.isValid()) {
          states[i] = editor.getState(FileEditorStateLevel.FULL);
        }
      }
      //noinspection SynchronizeOnThis
      synchronized (this) {
        myEntriesList.add(HistoryEntry.createHeavy(myProject, file, providers, states, providers[selectedProviderIndex]));
      }
      trimToSize();
    }
  }

  public void updateHistoryEntry(@Nullable final VirtualFile file, final boolean changeEntryOrderOnly) {
    updateHistoryEntry(file, null, null, changeEntryOrderOnly);
  }

  private void updateHistoryEntry(@Nullable final VirtualFile file,
                                  @Nullable final FileEditor fallbackEditor,
                                  @Nullable FileEditorProvider fallbackProvider,
                                  final boolean changeEntryOrderOnly) {
    if (file == null) {
      return;
    }
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(myProject);
    final Pair<FileEditor[], FileEditorProvider[]> editorsWithProviders = editorManager.getEditorsWithProviders(file);
    FileEditor[] editors = editorsWithProviders.getFirst();
    FileEditorProvider[] providers = editorsWithProviders.getSecond();
    if (editors.length <= 0 && fallbackEditor != null) {
      editors = new FileEditor[] {fallbackEditor};
      providers = new FileEditorProvider[] {fallbackProvider};
    }

    if (editors.length == 0) {
      // obviously not opened in any editor at the moment,
      // makes no sense to put the file in the history
      return;
    }
    final HistoryEntry entry = getEntry(file);
    if(entry == null){
      // Size of entry list can be less than number of opened editors (some entries can be removed)
      if (file.isValid()) {
        // the file could have been deleted, so the isValid() check is essential
        fileOpenedImpl(file, fallbackEditor, fallbackProvider);
      }
      return;
    }

    if (!changeEntryOrderOnly) { // update entry state
      //LOG.assertTrue(editors.length > 0);
      for (int i = editors.length - 1; i >= 0; i--) {
        final FileEditor           editor = editors   [i];
        final FileEditorProvider provider = providers [i];
        if (provider == null) continue; // can happen if fallbackProvider is null
        if (!editor.isValid()) {
          // this can happen for example if file extension was changed
          // and this method was called during corresponding myEditor close up
          continue;
        }

        final FileEditorState oldState = entry.getState(provider);
        final FileEditorState newState = editor.getState(FileEditorStateLevel.FULL);
        if (!newState.equals(oldState)) {
          entry.putState(provider, newState);
        }
      }
    }
    final Pair <FileEditor, FileEditorProvider> selectedEditorWithProvider = editorManager.getSelectedEditorWithProvider(file);
    if (selectedEditorWithProvider != null) {
      //LOG.assertTrue(selectedEditorWithProvider != null);
      entry.setSelectedProvider(selectedEditorWithProvider.getSecond());
      LOG.assertTrue(entry.getSelectedProvider() != null);

      if(changeEntryOrderOnly){
        moveOnTop(entry);
      }
    }
  }

  /**
   * @return array of valid files that are in the history, oldest first.
   */
  @NotNull
  public synchronized VirtualFile[] getFiles() {
    List<VirtualFile> result = new ArrayList<>(myEntriesList.size());
    for (HistoryEntry entry : myEntriesList) {
      VirtualFile file = entry.getFile();
      if (file != null) result.add(file);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @TestOnly
  public synchronized void removeAllFiles() {
    for (HistoryEntry entry : myEntriesList) {
      entry.destroy();
    }
    myEntriesList.clear();
  }

  /**
   * @return a set of valid files that are in the history, oldest first.
   */
  @NotNull
  public synchronized List<VirtualFile> getFileList() {
    List<VirtualFile> result = new ArrayList<>();
    for (HistoryEntry entry : myEntriesList) {
      VirtualFile file = entry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return result;
  }

  @NotNull
  @Deprecated
  public synchronized LinkedHashSet<VirtualFile> getFileSet() {
    return new LinkedHashSet<>(getFileList());
  }

  public synchronized boolean hasBeenOpen(@NotNull VirtualFile f) {
    for (HistoryEntry each : myEntriesList) {
      if (f.equals(each.getFile())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes specified {@code file} from history. The method does
   * nothing if {@code file} is not in the history.
   *
   * @exception IllegalArgumentException if {@code file}
   * is {@code null}
   */
  public synchronized void removeFile(@NotNull final VirtualFile file){
    final HistoryEntry entry = getEntry(file);
    if(entry != null){
      removeEntry(entry);
    }
  }

  public FileEditorState getState(@NotNull VirtualFile file, final FileEditorProvider provider) {
    final HistoryEntry entry = getEntry(file);
    return entry != null ? entry.getState(provider) : null;
  }

  /**
   * @return may be null
   */
  public FileEditorProvider getSelectedProvider(final VirtualFile file) {
    final HistoryEntry entry = getEntry(file);
    return entry != null ? entry.getSelectedProvider() : null;
  }

  private synchronized HistoryEntry getEntry(@NotNull VirtualFile file) {
    for (int i = myEntriesList.size() - 1; i >= 0; i--) {
      final HistoryEntry entry = myEntriesList.get(i);
      VirtualFile entryFile = entry.getFile();
      if (file.equals(entryFile)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * If total number of files in history more then {@code UISettings.RECENT_FILES_LIMIT}
   * then removes the oldest ones to fit the history to new size.
   */
  private synchronized void trimToSize() {
    final int limit = UISettings.getInstance().getRecentFilesLimit() + 1;
    while (myEntriesList.size() > limit) {
      HistoryEntry removed = myEntriesList.remove(0);
      removed.destroy();
    }
  }

  @Override
  public synchronized void loadState(@NotNull Element state) {
    myEntriesList.clear();
    // backward compatibility - previously entry maybe duplicated
    Map<String, Element> fileToElement = new LinkedHashMap<>();
    for (Element e : state.getChildren(HistoryEntry.TAG)) {
      String file = e.getAttributeValue(HistoryEntry.FILE_ATTR);
      fileToElement.remove(file);
      // last is the winner
      fileToElement.put(file, e);
    }

    for (Element e : fileToElement.values()) {
      try {
        myEntriesList.add(HistoryEntry.createHeavy(myProject, e));
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Exception anyException) {
        LOG.error(anyException);
      }
    }
  }

  @Override
  public synchronized Element getState() {
    Element element = new Element("state");
    // update history before saving
    final VirtualFile[] openFiles = FileEditorManager.getInstance(myProject).getOpenFiles();
    for (int i = openFiles.length - 1; i >= 0; i--) {
      final VirtualFile file = openFiles[i];
      // we have to update only files that are in history
      if (getEntry(file) != null) {
        updateHistoryEntry(file, false);
      }
    }

    for (final HistoryEntry entry : myEntriesList) {
      entry.writeExternal(element, myProject);
    }
    return element;
  }

  @Override
  public synchronized void dispose() {
    for (HistoryEntry entry : myEntriesList) {
      entry.destroy();
    }
    myEntriesList.clear();
  }
  
  /**
   * Updates history
   */
  private final class MyEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file){
      fileOpenedImpl(file, null, null);
    }

    @Override
    public void selectionChanged(@NotNull final FileEditorManagerEvent event){
      // updateHistoryEntry does commitDocument which is 1) very expensive and 2) cannot be performed from within PSI change listener
      // so defer updating history entry until documents committed to improve responsiveness
      PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(() -> {
        FileEditor newEditor = event.getNewEditor();
        if(newEditor != null && !newEditor.isValid())
          return;

        updateHistoryEntry(event.getOldFile(), event.getOldEditor(), event.getOldProvider(), false);
        updateHistoryEntry(event.getNewFile(), true);
      });
    }
  }
}
