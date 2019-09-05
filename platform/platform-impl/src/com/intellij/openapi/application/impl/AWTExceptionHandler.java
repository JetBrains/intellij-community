// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

public final class AWTExceptionHandler {
  public static void register() {
    System.setProperty("sun.awt.exception.handler", AWTExceptionHandler.class.getName());
  }

  // the method must be here
  @SuppressWarnings({"UnusedParameters"})
  public void handle(Throwable e) {
    // error has already been logged
    // do nothing, do not crash AWT
  }
}
