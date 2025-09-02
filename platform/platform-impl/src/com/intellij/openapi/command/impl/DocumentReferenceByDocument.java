// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DocumentReferenceByDocument implements DocumentReference {
  private final Document myDocument;

  DocumentReferenceByDocument(@NotNull Document document) {
    myDocument = document;
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @Nullable VirtualFile getFile() {
    return null;
  }

  @Override
  public String toString() {
    if (myDocument instanceof DocumentImpl) {
      return myDocument.toString();
    }
    // myDocument.toString() is not allowed here because of StackOverflowError, see IJPL-198678
    // DocumentReferenceByDocument.toString()
    //   BasicUndoableAction.toString()
    //   UndoRedoList.toString()
    //   UserDataHolderBase.toString() [STACK_IN_DOCUMENT_KEY]
    //   DiagramDocumentAdapter.toString()
    //   DocumentReferenceByDocument.toString()
    //   BasicUndoableAction.toString()
    //   UndoRedoList.toString()
    //   UserDataHolderBase.toString() [STACK_IN_DOCUMENT_KEY]
    //   DiagramDocumentAdapter.toString()
    //   ...
    //   UndoRedoList.toString()
    //   UndoableGroup.dumpState()
    //   ...
    String className = myDocument.getClass().getSimpleName();
    String instanceId = Integer.toHexString(System.identityHashCode(myDocument));
    VirtualFile file = FileDocumentManager.getInstance().getFile(myDocument);
    return className + "@" + instanceId + "{file=" + file + "}";
  }
}
