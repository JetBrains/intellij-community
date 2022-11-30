// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This component provides the notion of last editor action.
 * Its purpose is to be able to determine whether some action was performed right after another specific action.
 * <p>
 * It's supposed to be used from EDT only.
 */
@Service
public final class EditorLastActionTracker {
  private String myLastActionId;
  private Editor myCurrentEditor;
  private Editor myLastEditor;

  @NotNull
  public static EditorLastActionTracker getInstance() {
    return ApplicationManager.getApplication().getService(EditorLastActionTracker.class);
  }

  /**
   * Returns the id of the previously invoked action or {@code null}, if no history exists yet, or last user activity was of
   * non-action type, like mouse clicking in editor or text typing, or previous action was invoked for a different editor.
   */
  @Nullable
  public String getLastActionId() {
    return myLastActionId;
  }

  private static String getActionId(AnAction action) {
    return action instanceof ActionStub ? ((ActionStub)action).getId() : ActionManager.getInstance().getId(action);
  }

  private void doResetLastAction() {
    myLastActionId = null;
    myLastEditor = null;
  }

  @Nullable
  private static EditorLastActionTracker getTrackerIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(EditorLastActionTracker.class);
  }

  final static class MyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      EditorLastActionTracker tracker = getInstance();
      Editor killedEditor = event.getEditor();
      if (tracker.myCurrentEditor == killedEditor) {
        tracker.myCurrentEditor = null;
      }
      if (tracker.myLastEditor == killedEditor) {
        tracker.myLastEditor = null;
      }
    }
  }

  private static void resetLastAction() {
    EditorLastActionTracker tracker = getTrackerIfCreated();
    if (tracker != null) {
      tracker.doResetLastAction();
    }
  }

  final static class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
      Editor editor = event.getData(CommonDataKeys.HOST_EDITOR);
      EditorLastActionTracker tracker = editor == null ? getTrackerIfCreated() : getInstance();
      if (tracker == null) {
        return;
      }

      tracker.myCurrentEditor = editor;
      if (tracker.myCurrentEditor != tracker.myLastEditor) {
        tracker.doResetLastAction();
      }
    }

    @Override
    public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
      EditorLastActionTracker tracker = getInstance();
      tracker.myLastActionId = getActionId(action);
      tracker.myLastEditor = tracker.myCurrentEditor;
      tracker.myCurrentEditor = null;
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      resetLastAction();
    }
  }

  final static class MyEditorMouseListener implements EditorMouseListener {
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
  }
}
