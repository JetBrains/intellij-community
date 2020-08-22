// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class StartMarkAction extends BasicUndoableAction {
  public static final Key<StartMarkAction> START_MARK_ACTION_KEY = Key.create("current.inplace.refactorings.mark");
  private String myCommandName;
  private boolean myGlobal;
  private Document myDocument;

  private StartMarkAction(Editor editor, String commandName) {
    super(DocumentReferenceManager.getInstance().create(editor.getDocument()));
    myCommandName = commandName;
    myDocument = editor.getDocument();
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }

  public void setGlobal(boolean global) {
    myGlobal = global;
  }

  @Override
  public boolean isGlobal() {
    return myGlobal;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public void setCommandName(String commandName) {
    myCommandName = commandName;
  }

  public Document getDocument() {
    return myDocument;
  }

  @TestOnly
  public static void checkCleared(@Nullable Project project) {
    if (project == null) {
      return;
    }
    try {
      StartMarkAction markAction = project.getUserData(START_MARK_ACTION_KEY);
      assert markAction == null : markAction.myDocument;
    }
    finally {
      project.putUserData(START_MARK_ACTION_KEY, null);
    }
  }

  public static StartMarkAction start(Editor editor, Project project, String commandName) throws AlreadyStartedException {
    final StartMarkAction existingMark = project.getUserData(START_MARK_ACTION_KEY);
    if (existingMark != null) {
      throw new AlreadyStartedException(existingMark.myCommandName,
                                        existingMark.myDocument,
                                        existingMark.getAffectedDocuments());
    }
    final StartMarkAction markAction = new StartMarkAction(editor, commandName);
    UndoManager.getInstance(project).undoableActionPerformed(markAction);
    project.putUserData(START_MARK_ACTION_KEY, markAction);
    return markAction;
  }

  public static StartMarkAction canStart(Project project) {
    return project.getUserData(START_MARK_ACTION_KEY);
  }

  static void markFinished(Project project) {
    StartMarkAction existingMark = project.getUserData(START_MARK_ACTION_KEY);
    if (existingMark != null) {
      project.putUserData(START_MARK_ACTION_KEY, null);
      existingMark.myDocument = null;
    }
  }

  public static class AlreadyStartedException extends Exception {
    private final DocumentReference[] myAffectedDocuments;
    private final Document myDocument;

    public AlreadyStartedException(String commandName,
                                   Document document,
                                   DocumentReference[] documentRefs) {
      super("Unable to start inplace refactoring:\n" + IdeBundle.message("dialog.message.command.not.finished.yet", commandName));
      myAffectedDocuments = documentRefs;
      myDocument = document;
    }

    public DocumentReference[] getAffectedDocuments() {
      return myAffectedDocuments;
    }

    public Document getDocument() {
      return myDocument;
    }
  }
}
