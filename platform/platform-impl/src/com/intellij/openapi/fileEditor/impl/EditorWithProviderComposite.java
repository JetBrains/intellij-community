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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Author: msk
 */
public class EditorWithProviderComposite extends EditorComposite {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite");
  private final FileEditorProvider[] myProviders;
  private boolean myIsNavigation;
  private AnActionListener.Adapter myListener;
  private boolean myToMaterialize;

  EditorWithProviderComposite (
    final VirtualFile file,
    final FileEditor[] editors,
    final FileEditorProvider[] providers,
    final FileEditorManagerEx fileEditorManager
    ) {
    super(file, editors, fileEditorManager);
    myProviders = providers;
  }

  public FileEditorProvider[] getProviders() {
    return myProviders;
  }

  public boolean isModified() {
    final FileEditor [] editors = getEditors ();
    for (FileEditor editor : editors) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider() {
    LOG.assertTrue(myEditors.length > 0, myEditors.length);
    if(myEditors.length==1){
      LOG.assertTrue(myTabbedPaneWrapper==null);
      return Pair.create (myEditors[0], myProviders [0]);
    }
    else{ // we have to get myEditor from tabbed pane
      LOG.assertTrue(myTabbedPaneWrapper!=null);
      int index = myTabbedPaneWrapper.getSelectedIndex();
      if (index == -1) {
        index = 0;
      }
      LOG.assertTrue(index>=0, index);
      LOG.assertTrue(index<myEditors.length, index);
      return Pair.create (myEditors[index], myProviders [index]);
    }
  }

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
    return new HistoryEntry(getFile(), providers, states, providers[selectedProviderIndex]);
  }

  public boolean isForNavigation() {
    return myIsNavigation;
  }

  public void setForNavigation(boolean navigation) {
    myIsNavigation = navigation;
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return;
    }
    EditorWindow window = ((FileEditorManagerEx)getFileEditorManager()).getSplitters().getNavigationEditorWindow();
    if (window != null) {
      int index = window.findEditorIndex(this);
      if (index > 0) {
        window.setForegroundAt(index, JBColor.cyan);
      }
      if (navigation) {
        final TextEditor textEditor = ContainerUtil.findInstance(getEditors(), TextEditor.class);

        myListener = new AnActionListener.Adapter() {
          @Override
          public void beforeEditorTyping(char c, final DataContext dataContext) {
            final FileEditor data = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
            if (data == textEditor) {
              myToMaterialize = true;
              ActionManager.getInstance().removeAnActionListener(this);
              myListener = null;
            }
          }

          @Override
          public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
            FileEditor data = PlatformDataKeys.FILE_EDITOR.getData(event.getDataContext());
            if (data == textEditor &&
                action instanceof EditorAction &&
                ((EditorAction)action).getHandler() instanceof EditorWriteActionHandler) {
              //FileEditorManager.getInstance(PlatformDataKeys.PROJECT.getData(dataContext)).materializeNavigationTab(data);
              myToMaterialize = true;
              ActionManager.getInstance().removeAnActionListener(this);
              myListener = null;
            }
          }
        };
        ActionManager.getInstance().addAnActionListener(myListener);
      }
    }
  }

  public boolean isToMaterialize() {
    return myToMaterialize;
  }

  @Override
  public void dispose() {
    if (myListener != null) {
      ActionManager.getInstance().removeAnActionListener(myListener);
    }
    super.dispose();
  }
}
