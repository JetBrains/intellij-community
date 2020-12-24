// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

public class MultiCloseable implements AutoCloseable {
  private static final AutoCloseable @NotNull [] EMPTY_ARRAY = new AutoCloseable[0];
  private final @NotNull SmartList<AutoCloseable> myCleanupList = new SmartList<>();

  public void registerCloseable(@NotNull AutoCloseable closeable) {
    synchronized (myCleanupList) {
      myCleanupList.add(closeable);
    }
  }

  @Override
  public void close() throws Exception {
    AutoCloseable[] cleanupArray;
    synchronized (myCleanupList) {
      if (myCleanupList.isEmpty()) {
        return;
      }
      cleanupArray = myCleanupList.toArray(EMPTY_ARRAY);
      myCleanupList.clear();
    }

    closeAll(cleanupArray, true);
  }

  public static void closeAll(@NotNull AutoCloseable @NotNull [] cleanupArray, boolean reversed) throws Exception {
    Throwable throwable = null;
    for (int i = 0; i < cleanupArray.length; i++) {
      AutoCloseable closeable = cleanupArray[reversed ? cleanupArray.length - 1 - i : i];
      try {
        closeable.close();
      }
      catch (Throwable t) {
        if (throwable == null) {
          throwable = t;
        }
        else {
          throwable.addSuppressed(t);
        }
      }
    }
    if (throwable instanceof Exception) {
      throw (Exception)throwable;
    }
    ExceptionUtil.rethrowAllAsUnchecked(throwable);
  }
}
