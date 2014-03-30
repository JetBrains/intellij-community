/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class EditorHistoryManager extends AbstractProjectComponent implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorHistoryManager");
  private Element myElement;

  public static EditorHistoryManager getInstance(final Project project){
    return project.getComponent(EditorHistoryManager.class);
  }

  /**
   * State corresponding to the most recent file is the last
   */
  private final ArrayList<HistoryEntry> myEntriesList = new ArrayList<HistoryEntry>();

  /** Invoked by reflection */
  EditorHistoryManager(final Project project, final UISettings uiSettings){
    super(project);
    uiSettings.addUISettingsListener(new MyUISettingsListener(), project);
  }

  @Override
  public void projectOpened(){

    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new MyEditorManagerBeforeListener());
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorManagerListener());

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
      new DumbAwareRunnable() {
        @Override
        public void run() {
          // myElement may be null if node that corresponds to this manager does not exist
          if (myElement != null) {
            final List children = myElement.getChildren(HistoryEntry.TAG);
            myElement = null;
            //noinspection unchecked
            for (final Element e : (Iterable<Element>)children) {
              try {
                myEntriesList.add(new HistoryEntry(myProject, e));
              }
              catch (InvalidDataException e1) {
                // OK here
              }
              catch (ProcessCanceledException e1) {
                // OK here
              }
              catch (Exception anyException) {
                LOG.error(anyException);
              }
            }
            trimToSize();
          }
        }
      }
    );
  }

  @Override
  @NotNull
  public String getComponentName(){
    return "editorHistoryManager";
  }

  private void fileOpenedImpl(@NotNull final VirtualFile file) {
    fileOpenedImpl(file, null, null);
  }

  /**
   * Makes file most recent one
   */
  private void fileOpenedImpl(@NotNull final VirtualFile file,
                              @Nullable final FileEditor fallbackEditor,
                              @Nullable FileEditorProvider fallbackProvider)
  {
    ApplicationManager.getApplication().assertIsDispatchThread();
    // don't add files that cannot be found via VFM (light & etc.)
    if (VirtualFileManager.getInstance().findFileByUrl(file.getUrl()) == null) return;

    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(myProject);

    final Pair<FileEditor[], FileEditorProvider[]> editorsWithProviders = editorManager.getEditorsWithProviders(file);
    FileEditor[] editors = editorsWithProviders.getFirst();
    FileEditorProvider[] oldProviders = editorsWithProviders.getSecond();
    if (editors.length <= 0 && fallbackEditor != null) {
      editors = new FileEditor[] { fallbackEditor };
    }
    if (oldProviders.length <= 0 && fallbackProvider != null) {
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
    if(entry != null){
      myEntriesList.remove(entry);
      myEntriesList.add(entry);
    }
    else {
      final FileEditorState[] states=new FileEditorState[editors.length];
      final FileEditorProvider[] providers=new FileEditorProvider[editors.length];
      for (int i = states.length - 1; i >= 0; i--) {
        final FileEditorProvider provider = oldProviders [i];
        LOG.assertTrue(provider != null);
        providers[i] = provider;
        states[i] = editors[i].getState(FileEditorStateLevel.FULL);
      }
      myEntriesList.add(new HistoryEntry(file, providers, states, providers[selectedProviderIndex]));
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
      entry.mySelectedProvider = selectedEditorWithProvider.getSecond ();
      LOG.assertTrue(entry.mySelectedProvider != null);

      if(changeEntryOrderOnly){
        myEntriesList.remove(entry);
        myEntriesList.add(entry);
      }
    }
  }

  /**
   * Removes all entries that correspond to invalid files
   */
  private void validateEntries(){
    for(int i=myEntriesList.size()-1; i>=0; i--){
      final HistoryEntry entry = myEntriesList.get(i);
      if(!entry.myFile.isValid()){
        myEntriesList.remove(i);
      }
    }
  }

  /**
   * @return array of valid files that are in the history, oldest first. May contain duplicates.
   */
  public VirtualFile[] getFiles(){
    validateEntries();
    final VirtualFile[] result = new VirtualFile[myEntriesList.size()];
    for(int i=myEntriesList.size()-1; i>=0 ;i--){
      result[i] = myEntriesList.get(i).myFile;
    }
    return result;
  }

  /**
   * @return a set of valid files that are in the history, oldest first.
   */
  public LinkedHashSet<VirtualFile> getFileSet() {
    LinkedHashSet<VirtualFile> result = ContainerUtil.newLinkedHashSet();
    for (VirtualFile file : getFiles()) {
      // if the file occurs several times in the history, only its last occurrence counts 
      result.remove(file);
      result.add(file);
    }
    return result;
  }

  public boolean hasBeenOpen(@NotNull VirtualFile f) {
    for (HistoryEntry each : myEntriesList) {
      if (Comparing.equal(each.myFile, f)) return true;
    }
    return false;
  }

  /**
   * Removes specified <code>file</code> from history. The method does
   * nothing if <code>file</code> is not in the history.
   *
   * @exception java.lang.IllegalArgumentException if <code>file</code>
   * is <code>null</code>
   */
  public void removeFile(@NotNull final VirtualFile file){
    final HistoryEntry entry = getEntry(file);
    if(entry != null){
      myEntriesList.remove(entry);
    }
  }

  public FileEditorState getState(final VirtualFile file, final FileEditorProvider provider) {
    validateEntries();
    final HistoryEntry entry = getEntry(file);
    return entry != null ? entry.getState(provider) : null;
  }

  /**
   * @return may be null
   */
  public FileEditorProvider getSelectedProvider(final VirtualFile file) {
    validateEntries();
    final HistoryEntry entry = getEntry(file);
    return entry != null ? entry.mySelectedProvider : null;
  }

  private HistoryEntry getEntry(final VirtualFile file){
    validateEntries();
    for (int i = myEntriesList.size() - 1; i >= 0; i--) {
      final HistoryEntry entry = myEntriesList.get(i);
      if(file.equals(entry.myFile)){
        return entry;
      }
    }
    return null;
  }

  /**
   * If total number of files in history more then <code>UISettings.RECENT_FILES_LIMIT</code>
   * then removes the oldest ones to fit the history to new size.
   */
  private void trimToSize(){
    final int limit = UISettings.getInstance().RECENT_FILES_LIMIT + 1;
    while(myEntriesList.size()>limit){
      myEntriesList.remove(0);
    }
  }

  @Override
  public void readExternal(final Element element) {
    // we have to delay xml processing because history entries require EditorStates to be created
    // which is done via corresponding EditorProviders, those are not accessible before their
    // is initComponent() called
    myElement = element.clone();
  }

  @Override
  public void writeExternal(final Element element){
    // update history before saving
    final VirtualFile[] openFiles = FileEditorManager.getInstance(myProject).getOpenFiles();
    for (int i = openFiles.length - 1; i >= 0; i--) {
      final VirtualFile file = openFiles[i];
      if(getEntry(file) != null){ // we have to update only files that are in history
        updateHistoryEntry(file, false);
      }
    }

    for (final HistoryEntry entry : myEntriesList) {
      entry.writeExternal(element, myProject);
    }
  }

  /**
   * Updates history
   */
  private final class MyEditorManagerListener extends FileEditorManagerAdapter{
    @Override
    public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file){
      fileOpenedImpl(file);
    }

    @Override
    public void selectionChanged(@NotNull final FileEditorManagerEvent event){
      // updateHistoryEntry does commitDocument which is 1) very expensive and 2) cannot be performed from within PSI change listener
      // so defer updating history entry until documents committed to improve responsiveness
      PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(new Runnable() {
        @Override
        public void run() {
          updateHistoryEntry(event.getOldFile(), event.getOldEditor(), event.getOldProvider(), false);
          updateHistoryEntry(event.getNewFile(), true);
        }
      });
    }
  }

  private final class MyEditorManagerBeforeListener extends FileEditorManagerListener.Before.Adapter {
    @Override
    public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      updateHistoryEntry(file, false);
    }
  }

  /**
   * Cuts/extends history length
   */
  private final class MyUISettingsListener implements UISettingsListener{
    @Override
    public void uiSettingsChanged(final UISettings source) {
      trimToSize();
    }
  }
}
