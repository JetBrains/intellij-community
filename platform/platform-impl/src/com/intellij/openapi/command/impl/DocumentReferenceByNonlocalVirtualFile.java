// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DocumentReferenceByNonlocalVirtualFile implements DocumentReference {
  private final VirtualFile myFile;

  DocumentReferenceByNonlocalVirtualFile(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  public @Nullable Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(myFile);
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public String toString() {
    return myFile.toString();
  }
}
