// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;


final class UndoAffectedDocuments {
  private final @NotNull Collection<@NotNull DocumentReference> affected = new HashSet<>(1);

  boolean affects(@NotNull DocumentReference ref) {
    return affected.contains(ref);
  }

  boolean affectsOnlyPhysical() {
    if (affected.isEmpty()) {
      return false;
    }
    for (DocumentReference ref : affected) {
      if (isVirtualDocumentChange(ref.getFile())) {
        return false;
      }
    }
    return true;
  }

  boolean affectsMultiplePhysical() {
    var affectedFiles = new HashSet<VirtualFile>(2);
    for (DocumentReference doc : affected) {
      VirtualFile file = doc.getFile();
      if (isVirtualDocumentChange(file)) {
        continue;
      }
      affectedFiles.add(file);
      if (affectedFiles.size() > 1) {
        return true;
      }
    }
    return false;
  }

  @Nullable DocumentReference firstAffected() {
    if (affected.isEmpty()) {
      return null;
    }
    return affected.iterator().next();
  }

  int size() {
    return affected.size();
  }

  void addAffected(DocumentReference @Nullable ... refs) {
    if (refs != null) {
      for (DocumentReference ref : refs) {
        if (ref != null) {
          affected.add(ref);
        }
      }
    }
  }

  void addAffected(Document @NotNull ... docs) {
    DocumentReference[] refs = Arrays.stream(docs)
      .filter(doc -> {
        // is document's file still valid
        var file = FileDocumentManager.getInstance().getFile(doc);
        return file == null || file.isValid();
      })
      .map(doc -> DocumentReferenceManager.getInstance().create(doc))
      .toArray(DocumentReference[]::new);
    addAffected(refs);
  }

  void addAffected(VirtualFile @NotNull ... files) {
    DocumentReference[] refs = Arrays.stream(files)
      .map(doc -> DocumentReferenceManager.getInstance().create(doc))
      .toArray(DocumentReference[]::new);
    addAffected(refs);
  }

  void addAffected(@NotNull UndoAffectedDocuments undoAffectedDocuments) {
    affected.addAll(undoAffectedDocuments.affected);
  }

  void removeAffected(@NotNull DocumentReference ref) {
    affected.remove(ref);
  }

  @NotNull Collection<DocumentReference> asCollection() {
    return Collections.unmodifiableCollection(affected);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof UndoAffectedDocuments documents)) {
      return false;
    }
    return affected.equals(documents.affected);
  }

  @Override
  public int hashCode() {
    return affected.hashCode();
  }

  private static boolean isVirtualDocumentChange(@Nullable VirtualFile file) {
    return file == null || file instanceof LightVirtualFile;
  }
}
