// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorLastActionTracker;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EditorLastActionTrackerImpl implements EditorLastActionTracker {
  private String myLastActionId;
  private Editor myCurrentEditor;
  private Editor myLastEditor;

  EditorLastActionTrackerImpl() {
    EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new EditorMouseListener() {
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
    }, ApplicationManager.getApplication());
  }

  private static boolean is(Editor currentEditor, EditorImpl killedEditor) {
    return currentEditor == killedEditor || currentEditor instanceof EditorWindow && ((EditorWindow)currentEditor).getDelegate() == killedEditor;
  }

  @Override
  @Nullable
  public String getLastActionId() {
    return myLastActionId;
  }

  private static String getActionId(AnAction action) {
    return action instanceof ActionStub ? ((ActionStub)action).getId() : ActionManager.getInstance().getId(action);
  }

  private void resetLastAction() {
    myLastActionId = null;
    myLastEditor = null;
  }

  @NotNull
  private static EditorLastActionTrackerImpl getTracker() {
    return (EditorLastActionTrackerImpl)EditorLastActionTracker.getInstance();
  }

  final static class MyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      EditorLastActionTrackerImpl tracker = getTracker();
      EditorImpl killedEditor = (EditorImpl)event.getEditor();
      if (is(tracker.myCurrentEditor, killedEditor)) {
        tracker.myCurrentEditor = null;
      }
      if (is(tracker.myLastEditor, killedEditor)) {
        tracker.myLastEditor = null;
      }
    }
  }

  final static class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
      EditorLastActionTrackerImpl tracker = getTracker();
      tracker.myCurrentEditor = CommonDataKeys.EDITOR.getData(dataContext);
      if (tracker.myCurrentEditor != tracker.myLastEditor) {
        tracker.resetLastAction();
      }
    }

    @Override
    public void afterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
      EditorLastActionTrackerImpl tracker = getTracker();
      tracker.myLastActionId = getActionId(action);
      tracker.myLastEditor = tracker.myCurrentEditor;
      tracker.myCurrentEditor = null;
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      getTracker().resetLastAction();
    }
  }
}
