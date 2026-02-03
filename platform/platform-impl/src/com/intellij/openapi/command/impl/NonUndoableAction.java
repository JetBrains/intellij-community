// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

final class NonUndoableAction implements UndoableAction {
  private static final Logger LOG = Logger.getInstance(NonUndoableAction.class);
  private long myPerformedTimestamp = -1;
  private final DocumentReference[] myRefs;
  private final boolean myGlobal;

  NonUndoableAction(@NotNull DocumentReference ref, boolean isGlobal) {
    myGlobal = isGlobal;
    myRefs = new DocumentReference[]{ref};
    if (LOG.isDebugEnabled()) {
      LOG.debug("global=" + isGlobal + "; doc=" + ref);
    }
  }

  @Override
  public void undo() {
    LOG.error("Cannot undo");
  }

  @Override
  public void redo() {
    LOG.error("Cannot redo");
  }

  @Override
  public DocumentReference[] getAffectedDocuments() {
    return myRefs;
  }

  @Override
  public boolean isGlobal() {
    return myGlobal;
  }

  @Override
  public long getPerformedNanoTime() {
    return myPerformedTimestamp;
  }

  @Override
  public void setPerformedNanoTime(long l) {
    myPerformedTimestamp = l;
  }

  @Override
  public String toString() {
    return "NonUndoableAction{ref=%s%s, performed=%sms}"
      .formatted(myRefs[0], isGlobal() ? ", global" : "", getPerformedNanoTime() / 1000);
  }
}
