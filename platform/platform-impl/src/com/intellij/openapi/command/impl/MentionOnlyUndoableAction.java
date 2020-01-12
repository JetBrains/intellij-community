/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

/**
 * Used to make Undo/Redo action available for some Document, even if it was not modified.
 * Undo action can be available even if this Document is ReadOnly.
 */
class MentionOnlyUndoableAction implements UndoableAction {
  private final DocumentReference[] myRefs;

  protected MentionOnlyUndoableAction(DocumentReference @NotNull [] refs) {
    myRefs = refs;
  }

  @Override
  public final void undo() {
  }

  @Override
  public final void redo() {
  }

  @Override
  public DocumentReference[] getAffectedDocuments() {
    return myRefs;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }
}
