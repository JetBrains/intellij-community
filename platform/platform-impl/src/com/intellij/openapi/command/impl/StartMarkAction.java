// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

public class StartMarkAction extends BasicUndoableAction {
  private static final Map<Project, StartMarkAction> ourCurrentMarks = new HashMap<>();
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
  public static void checkCleared() {
    try {
      assert ourCurrentMarks.isEmpty() : ourCurrentMarks.values();
    }
    finally {
      ourCurrentMarks.clear();
    }
  }

  public static StartMarkAction start(Editor editor, Project project, String commandName) throws AlreadyStartedException {
    final StartMarkAction existingMark = ourCurrentMarks.get(project);
    if (existingMark != null) {
      throw new AlreadyStartedException(existingMark.myCommandName,
                                        existingMark.myDocument,
                                        existingMark.getAffectedDocuments());
    }
    final StartMarkAction markAction = new StartMarkAction(editor, commandName);
    UndoManager.getInstance(project).undoableActionPerformed(markAction);
    ourCurrentMarks.put(project, markAction);
    return markAction;
  }

  public static StartMarkAction canStart(Project project) {
    return ourCurrentMarks.get(project);
  }

  static void markFinished(Project project) {
    final StartMarkAction existingMark = ourCurrentMarks.remove(project);
    if (existingMark != null) {
      existingMark.myDocument = null;
    }
  }

  public static class AlreadyStartedException extends Exception {
    private final DocumentReference[] myAffectedDocuments;
    private final Document myDocument;

    public AlreadyStartedException(String commandName,
                                   Document document,
                                   DocumentReference[] documentRefs) {
      super("Unable to start inplace refactoring:\n"+ commandName + " is not finished yet.");
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
