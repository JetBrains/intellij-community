// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@Service
public final class VisibleEditorsTracker {
  private final Set<Editor> myEditorsVisibleOnCommandStart = new HashSet<>();
  private long myCurrentCommandStart;
  private long myLastCommandFinish;

  public static VisibleEditorsTracker getInstance() {
    return ApplicationManager.getApplication().getService(VisibleEditorsTracker.class);
  }

  final static class MyCommandListener implements CommandListener {
    @Override
    public void commandStarted(@NotNull CommandEvent event) {
      getInstance().commandStarted();
    }

    @Override
    public void commandFinished(@NotNull CommandEvent event) {
      getInstance().commandFinished();
    }
  }

  public boolean wasEditorVisibleOnCommandStart(Editor editor) {
    return myEditorsVisibleOnCommandStart.contains(editor);
  }

  public long getCurrentCommandStart() {
    return myCurrentCommandStart;
  }

  public long getLastCommandFinish() {
    return myLastCommandFinish;
  }

  private void commandStarted() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getComponent().isShowing()) {
        myEditorsVisibleOnCommandStart.add(editor);
      }

      ((ScrollingModelImpl)editor.getScrollingModel()).finishAnimation();
      myCurrentCommandStart = System.currentTimeMillis();
    }
  }

  private void commandFinished() {
    myEditorsVisibleOnCommandStart.clear();
    myLastCommandFinish = System.currentTimeMillis();
  }
}
