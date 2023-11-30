// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps;

import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.helpers.SharedMetrics;

import java.util.concurrent.Semaphore;

public final class JpsMetrics extends SharedMetrics {
  private static final Semaphore lock = new Semaphore(1);
  private static JpsMetrics instance = null;

  private JpsMetrics() { super(PlatformScopesKt.JPS); }

  public static JpsMetrics getInstance() {
    try {
      if (instance != null) return instance;
      lock.acquire();
      if (instance == null) instance = new JpsMetrics();
    }
    catch (InterruptedException e) {
      lock.release();
    }
    finally {
      lock.release();
    }

    return instance;
  }

  public static final String jpsSyncSpanName = "jps.sync";
}
