package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;

public class NonUndoableAction implements UndoableAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.undo.NonUndoableAction");

  private final DocumentReference[] myRefs;
  private final boolean myGlobal;

  protected NonUndoableAction(DocumentReference ref, boolean isGlobal) {
    myGlobal = isGlobal;
    myRefs = new DocumentReference[]{ref};
  }

  public final void undo() throws UnexpectedUndoException {
    LOG.assertTrue(false);
  }

  public void redo() throws UnexpectedUndoException {
    LOG.assertTrue(false);
  }

  public DocumentReference[] getAffectedDocuments() {
    return myRefs;
  }

  public boolean isGlobal() {
    return myGlobal;
  }
}