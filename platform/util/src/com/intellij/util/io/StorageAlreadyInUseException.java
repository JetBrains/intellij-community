// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import java.io.IOException;

/**
 * Thrown if an opening storage detected to be already opened/used by somebody else.
 */
public class StorageAlreadyInUseException extends IOException {
  public StorageAlreadyInUseException(String message) {
    super(message);
  }

  public StorageAlreadyInUseException(String message, Throwable cause) {
    super(message, cause);
  }
}
