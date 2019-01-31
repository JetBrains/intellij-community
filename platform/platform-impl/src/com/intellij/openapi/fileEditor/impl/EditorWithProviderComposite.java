// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Author: msk
 */
public class EditorWithProviderComposite extends EditorComposite {
  private static final Logger LOG = Logger.getInstance(EditorWithProviderComposite.class);
  private FileEditorProvider[] myProviders;

  EditorWithProviderComposite(@NotNull VirtualFile file,
                              @NotNull FileEditor[] editors,
                              @NotNull FileEditorProvider[] providers,
                              @NotNull FileEditorManagerEx fileEditorManager) {
    super(file, editors, fileEditorManager);
    myProviders = providers;
  }

  @NotNull
  public FileEditorProvider[] getProviders() {
    return myProviders;
  }

  @Override
  public boolean isModified() {
    final FileEditor[] editors = getEditors();
    for (FileEditor editor : editors) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public FileEditorWithProvider getSelectedWithProvider() {
    LOG.assertTrue(myEditors.length > 0, myEditors.length);
    if (myEditors.length == 1) {
      LOG.assertTrue(myTabbedPaneWrapper == null);
      return new FileEditorWithProvider(myEditors[0], myProviders[0]);
    }
    else {
      // we have to get myEditor from tabbed pane
      LOG.assertTrue(myTabbedPaneWrapper != null);
      int index = myTabbedPaneWrapper.getSelectedIndex();
      if (index == -1) {
        index = 0;
      }
      LOG.assertTrue(index >= 0, index);
      LOG.assertTrue(index < myEditors.length, index);
      return new FileEditorWithProvider(myEditors[index], myProviders[index]);
    }
  }

  @NotNull
  public HistoryEntry currentStateAsHistoryEntry() {
    final FileEditor[] editors = getEditors();
    final FileEditorState[] states = new FileEditorState[editors.length];
    for (int j = 0; j < states.length; j++) {
      states[j] = editors[j].getState(FileEditorStateLevel.FULL);
      LOG.assertTrue(states[j] != null);
    }
    final int selectedProviderIndex = ArrayUtil.find(editors, getSelectedEditor());
    LOG.assertTrue(selectedProviderIndex != -1);
    final FileEditorProvider[] providers = getProviders();
    return HistoryEntry.createLight(getFile(), providers, states, providers[selectedProviderIndex]);
  }

  public void addEditor(@NotNull FileEditor editor, FileEditorProvider provider) {
    addEditor(editor);
    myProviders = ArrayUtil.append(myProviders, provider);
  }
}
