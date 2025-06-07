// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class CurrentContentRevision implements ByteBackedContentRevision {
  protected FilePath myFile;

  public CurrentContentRevision(FilePath file) {
    myFile = file;
  }

  @Override
  public @Nullable @NonNls String getContent() {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) {
      return null;
    }
    Document doc = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(vFile));
    return doc == null ? null : doc.getText();
  }

  @Override
  public byte @Nullable [] getContentAsBytes() throws VcsException {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) {
      return null;
    }
    try {
      return vFile.contentsToByteArray();
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  public @Nullable VirtualFile getVirtualFile() {
    VirtualFile vFile = myFile.getVirtualFile();
    return vFile == null || !vFile.isValid() ? null : vFile;
  }

  @Override
  public @NotNull FilePath getFile() {
    return myFile;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }

  public static @NotNull ContentRevision create(@NotNull FilePath file) {
    if (file.getFileType().isBinary()) {
      return new CurrentBinaryContentRevision(file);
    }
    return new CurrentContentRevision(file);
  }

  @Override
  public @NonNls String toString() {
    return "CurrentContentRevision:" + myFile;
  }
}
