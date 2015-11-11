/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

class NonUndoableAction implements UndoableAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.undo.NonUndoableAction");

  private final DocumentReference[] myRefs;
  private final boolean myGlobal;

  protected NonUndoableAction(@NotNull DocumentReference ref, boolean isGlobal) {
    myGlobal = isGlobal;
    myRefs = new DocumentReference[]{ref};
    if (LOG.isDebugEnabled()) {
      LOG.debug("global=" + isGlobal + "; doc=" + ref, new Throwable());
    }
  }

  @Override
  public final void undo() {
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
}
