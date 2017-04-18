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
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorLastActionTracker;
import com.intellij.openapi.editor.event.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorLastActionTrackerImpl extends EditorMouseAdapter
  implements AnActionListener, EditorMouseListener, Disposable, EditorLastActionTracker, ApplicationComponent {
  private final ActionManager myActionManager;
  private final EditorEventMulticaster myEditorEventMulticaster;

  private String myLastActionId;
  private Editor myCurrentEditor;
  private Editor myLastEditor;

  EditorLastActionTrackerImpl(ActionManager actionManager, EditorFactory editorFactory) {
    myActionManager = actionManager;
    myEditorEventMulticaster = editorFactory.getEventMulticaster();
    // to prevent leaks
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        EditorImpl killedEditor = (EditorImpl)event.getEditor();
        if (is(myCurrentEditor, killedEditor)) {
          myCurrentEditor = null;
        }
        if (is(myLastEditor, killedEditor)) {
          myLastEditor = null;
        }
      }
    }, this);
  }

  private static boolean is(Editor currentEditor, EditorImpl killedEditor) {
    return currentEditor == killedEditor || currentEditor instanceof EditorWindow && ((EditorWindow)currentEditor).getDelegate() == killedEditor;
  }

  @Override
  public void initComponent() {
    myActionManager.addAnActionListener(this, this);
    myEditorEventMulticaster.addEditorMouseListener(this, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  @Nullable
  public String getLastActionId() {
    return myLastActionId;
  }

  @Override
  public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    myCurrentEditor = CommonDataKeys.EDITOR.getData(dataContext);
    if (myCurrentEditor != myLastEditor) {
      resetLastAction();
    }
  }

  @Override
  public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    myLastActionId = getActionId(action);
    myLastEditor = myCurrentEditor;
    myCurrentEditor = null;
  }

  @Override
  public void beforeEditorTyping(char c, DataContext dataContext) {
    resetLastAction();
  }

  @Override
  public void mousePressed(EditorMouseEvent e) {
    resetLastAction();
  }

  @Override
  public void mouseClicked(EditorMouseEvent e) {
    resetLastAction();
  }

  @Override
  public void mouseReleased(EditorMouseEvent e) {
    resetLastAction();
  }

  private String getActionId(AnAction action) {
    return action instanceof ActionStub ? ((ActionStub)action).getId() : myActionManager.getId(action);
  }

  private void resetLastAction() {
    myLastActionId = null;
    myLastEditor = null;
  }
}
