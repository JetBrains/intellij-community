// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class EDT {
  private static final Thread myEventDispatchThread;

  static {
    assert EventQueue.isDispatchThread() : Thread.currentThread();
    myEventDispatchThread = Thread.currentThread();
  }

  private EDT() {
  }

  public static boolean isEdt(@NotNull Thread thread) {
    return thread == myEventDispatchThread;
  }

  public static boolean isCurrentThreadEdt() {
    return isEdt(Thread.currentThread());
  }

  public static void assertIsEdt() {
    if (!isCurrentThreadEdt()) {
      Logger.getInstance(EDT.class).error("Assert: must be called on EDT");
    }
  }
}
