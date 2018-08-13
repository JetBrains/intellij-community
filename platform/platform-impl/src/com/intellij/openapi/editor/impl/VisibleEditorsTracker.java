// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;

import java.util.HashSet;
import java.util.Set;

public class VisibleEditorsTracker implements CommandListener {
  private final Set<Editor> myEditorsVisibleOnCommandStart = new HashSet<>();
  private long myCurrentCommandStart;
  private long myLastCommandFinish;

  public static VisibleEditorsTracker getInstance() {
    return ApplicationManager.getApplication().getComponent(VisibleEditorsTracker.class);
  }

  public VisibleEditorsTracker() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(CommandListener.TOPIC, this);
  }

  public boolean wasEditorVisibleOnCommandStart(Editor editor){
    return myEditorsVisibleOnCommandStart.contains(editor);
  }

  public long getCurrentCommandStart() { return myCurrentCommandStart; }

  public long getLastCommandFinish() { return myLastCommandFinish; }

  @Override
  public void commandStarted(CommandEvent event) {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getComponent().isShowing()) {
        myEditorsVisibleOnCommandStart.add(editor);
      }

      ((ScrollingModelImpl)editor.getScrollingModel()).finishAnimation();
      myCurrentCommandStart = System.currentTimeMillis();
    }
  }

  @Override
  public void commandFinished(CommandEvent event) {
    myEditorsVisibleOnCommandStart.clear();
    myLastCommandFinish = System.currentTimeMillis();
  }
}
