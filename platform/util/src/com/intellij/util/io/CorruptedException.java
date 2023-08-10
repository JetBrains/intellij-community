// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public class CorruptedException extends IOException {
  public CorruptedException(Path file) {
    this("Storage corrupted " + file);
  }

  public CorruptedException(String message) {
    super(message);
  }

  public CorruptedException(String message,
                            Throwable cause) {
    super(message, cause);
  }
}
