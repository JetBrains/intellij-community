/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DocumentReferenceByDocument implements DocumentReference {
  private final Document myDocument;

  DocumentReferenceByDocument(@NotNull Document document) {
    myDocument = document;
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  @Nullable
  public VirtualFile getFile() {
    return null;
  }

  @Override
  public String toString() {
    CharSequence text = myDocument.getCharsSequence();
    return text.subSequence(0, Math.min(80, text.length())).toString();
  }
}
