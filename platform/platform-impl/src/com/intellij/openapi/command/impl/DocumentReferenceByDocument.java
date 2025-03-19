// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
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
    CharSequence text = myDocument.getCharsSequence();
    return text.subSequence(0, Math.min(80, text.length())).toString();
  }
}
