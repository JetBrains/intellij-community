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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class EditorPlace extends JComponent implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace");
  private final MergePanel2.DiffEditorState myState;
  private final ArrayList<EditorListener> myListeners = new ArrayList<EditorListener>();
  private Editor myEditor = null;

  public EditorPlace(MergePanel2.DiffEditorState state) {
    myState = state;
    setLayout(new BorderLayout());
  }

  public void addNotify() {
    if (myEditor != null) {
      super.addNotify();
      return;
    }
    createEditor();
    super.addNotify();
    revalidate();
  }

  private void createEditor() {
    LOG.assertTrue(myEditor == null);
    myEditor = myState.createEditor();
    if (myEditor == null) return;
    add(myEditor.getComponent(), BorderLayout.CENTER);
    fireEditorCreated();
  }

  public void addListener(EditorListener listener) {
    myListeners.add(listener);
  }

  private void fireEditorCreated() {
    EditorListener[] listeners = getListeners();
    for (EditorListener listener : listeners) {
      listener.onEditorCreated(this);
    }
  }

  private void fireEditorReleased(Editor releasedEditor) {
    EditorListener[] listeners = getListeners();
    for (EditorListener listener : listeners) {
      listener.onEditorReleased(releasedEditor);
    }
  }

  private EditorListener[] getListeners() {
    EditorListener[] listeners = new EditorListener[myListeners.size()];
    myListeners.toArray(listeners);
    return listeners;
  }

  public void removeNotify() {
    removeEditor();
    super.removeNotify();
  }

  private void removeEditor() {
    if (myEditor != null) {
      Editor releasedEditor = myEditor;
      remove(myEditor.getComponent());
      getEditorFactory().releaseEditor(myEditor);
      myEditor = null;
      fireEditorReleased(releasedEditor);
    }
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void setDocument(Document document) {
    if (document == getDocument()) return;
    removeEditor();
    myState.setDocument(document);
    createEditor();
  }

  private Document getDocument() {
    return myState.getDocument();
  }

  public MergePanel2.DiffEditorState getState() {
    return myState;
  }

  public interface EditorListener {
    void onEditorCreated(EditorPlace place);
    void onEditorReleased(Editor releasedEditor);
  }

  private static EditorFactory getEditorFactory() {
    return EditorFactory.getInstance();
  }

  @Nullable
  public JComponent getContentComponent() {
    return myEditor == null ? null : myEditor.getContentComponent();
  }

  public void dispose() {
    removeEditor();
  }
}
