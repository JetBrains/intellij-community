// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorLastActionTracker;
import com.intellij.openapi.editor.event.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorLastActionTrackerImpl implements AnActionListener, EditorMouseListener, Disposable, EditorLastActionTracker, BaseComponent {
  private final ActionManager myActionManager;
  private final EditorEventMulticaster myEditorEventMulticaster;

  private String myLastActionId;
  private Editor myCurrentEditor;
  private Editor myLastEditor;

  EditorLastActionTrackerImpl(ActionManager actionManager, EditorFactory editorFactory) {
    myActionManager = actionManager;
    myEditorEventMulticaster = editorFactory.getEventMulticaster();
    // to prevent leaks
    editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
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
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(TOPIC, this);
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
  public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
    myCurrentEditor = CommonDataKeys.EDITOR.getData(dataContext);
    if (myCurrentEditor != myLastEditor) {
      resetLastAction();
    }
  }

  @Override
  public void afterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
    myLastActionId = getActionId(action);
    myLastEditor = myCurrentEditor;
    myCurrentEditor = null;
  }

  @Override
  public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
    resetLastAction();
  }

  @Override
  public void mousePressed(@NotNull EditorMouseEvent e) {
    resetLastAction();
  }

  @Override
  public void mouseClicked(@NotNull EditorMouseEvent e) {
    resetLastAction();
  }

  @Override
  public void mouseReleased(@NotNull EditorMouseEvent e) {
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
