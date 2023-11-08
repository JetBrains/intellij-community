// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps;

import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.helpers.SharedMetrics;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;

public final class JpsMetrics extends SharedMetrics {
  private static final @NotNull ClearableLazyValue<JpsMetrics> _instance = ClearableLazyValue.createAtomic(() -> new JpsMetrics());

  private JpsMetrics() { super(PlatformScopesKt.JPS); }

  public static JpsMetrics getInstance() {
    return _instance.getValue();
  }

  public static final String jpsSyncSpanName = "jps.sync";
}
