// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import org.jetbrains.annotations.ApiStatus;

/**
 * VFS requested to do something with a file that was deleted.
  */
@ApiStatus.Internal
public class FileDeletedException extends RuntimeException /*implements ControlFlowException?*/ {
  private final int deletedFileId;

  public FileDeletedException(int fileId) {
    super("file[#" + fileId + "] already is deleted");
    deletedFileId = fileId;
  }

  public FileDeletedException(int fileId,
                              String message) {
    super("file[#" + fileId + "]: " + message);
    this.deletedFileId = fileId;
  }

  public FileDeletedException(int fileId,
                              String message,
                              Throwable cause) {
    super("file[#" + fileId + "]: " + message, cause);
    this.deletedFileId = fileId;
  }

  public int getDeletedFileId() {
    return deletedFileId;
  }
}
