// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NonNls;

/**
 * @deprecated For local usage only, don't commit
 */
@Deprecated
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class PrintlnLogger extends DefaultLogger {
  public PrintlnLogger(String category) {
    super(category);
  } 
  
  public PrintlnLogger() {
    this("");
  }

  @Override
  public boolean isDebugEnabled() {
    return true;
  }

  @Override
  public void debug(String message) {
    System.out.println(message);
  }

  @Override
  public void debug(Throwable t) {
    if (t != null) {
      t.printStackTrace(System.out);
    }
  }

  @Override
  public void debug(@NonNls String message, Throwable t) {
    System.out.println(message);
    if (t != null) {
      t.printStackTrace(System.out);
    }
  }

  @Override
  public void info(String message) {
    System.out.println(message);
  }

  @Override
  public void info(String message, Throwable t) {
    if (t != null) {
      t.printStackTrace(System.out);
    }
  }
}
