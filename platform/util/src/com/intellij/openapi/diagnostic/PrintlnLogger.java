// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

/** @deprecated For local usage only, don't commit */
@Deprecated
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "unused"})
public class PrintlnLogger extends DefaultLogger {
  public PrintlnLogger() {
    this("");
  }

  public PrintlnLogger(String category) {
    super(category);
  }

  @Override
  public boolean isDebugEnabled() {
    return true;
  }

  @Override
  public void debug(String message, Throwable t) {
    System.out.println(message);
    if (t != null) {
      t.printStackTrace(System.out);
    }
  }

  @Override
  public void info(String message, Throwable t) {
    if (t != null) {
      t.printStackTrace(System.out);
    }
  }
}
