// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import java.io.IOException;
import java.nio.file.Path;

public class CorruptedException extends IOException {
  public CorruptedException(Path file) {
    this("Storage corrupted " + file);
  }

  protected CorruptedException(String message) {
    super(message);
  }

  //MAYBE RC: why there is no ctor with 'cause'?
}
