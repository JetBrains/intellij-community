// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

public final class EdtTestUtil {
  @kotlin.Deprecated(message = "Use Kotlin runInEdtAndGet { ... } instead")  // this warning is only visible in Kotlin files
  @TestOnly
  public static <V, T extends Throwable> V runInEdtAndGet(@NotNull ThrowableComputable<V, T> computable) throws T {
    final Ref<V> res = new Ref<>();
    runInEdtAndWait(() -> {
      res.set(computable.compute());
    });
    return res.get();
  }

  @kotlin.Deprecated(message = "Use Kotlin runInEdtAndWait { ... } instead")  // this warning is only visible in Kotlin files
  @TestOnly
  public static <T extends Throwable> void runInEdtAndWait(@NotNull ThrowableRunnable<T> runnable) throws T {
    Application app = ApplicationManager.getApplication();
    if (app == null ? EDT.isCurrentThreadEdt() : app.isDispatchThread()) {
      // reduce stack trace
      runnable.run();
      return;
    }

    Ref<T> exception = new Ref<>();
    Runnable r = () -> {
      try {
        runnable.run();
      }
      catch (Throwable e) {
        //noinspection unchecked
        exception.set((T)e);
      }
    };

    if (app != null) {
      app.invokeAndWait(r);
    }
    else {
      try {
        SwingUtilities.invokeAndWait(r);
      }
      catch (InterruptedException | InvocationTargetException e) {
        // must not happen
        throw new RuntimeException(e);
      }
    }

    if (!exception.isNull()) {
      throw exception.get();
    }
  }
}
