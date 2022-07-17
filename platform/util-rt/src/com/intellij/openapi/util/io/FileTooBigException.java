// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import java.io.IOException;

public final class FileTooBigException extends IOException {
  public FileTooBigException(String e) {
    super(e);
  }
}
