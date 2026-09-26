// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.cls;

public final class ClsFormatException extends Exception {
  public ClsFormatException() { }

  public ClsFormatException(String message) {
    super(message);
  }

  public ClsFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
