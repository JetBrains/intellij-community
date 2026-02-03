// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import java.io.IOException;

/**
 * On attempt to do something with a record which is deleted
 */
public final class RecordAlreadyDeletedException extends IOException {

  public RecordAlreadyDeletedException() {
  }

  public RecordAlreadyDeletedException(String message) {
    super(message);
  }

  public RecordAlreadyDeletedException(String message, Throwable cause) {
    super(message, cause);
  }

  public RecordAlreadyDeletedException(Throwable cause) {
    super(cause);
  }
}
