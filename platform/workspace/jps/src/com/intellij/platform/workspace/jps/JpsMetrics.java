// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps;

import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.helpers.SharedMetrics;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class JpsMetrics extends SharedMetrics {
  private static final @NotNull Supplier<JpsMetrics> _instance = new SynchronizedClearableLazy<>(() -> new JpsMetrics());

  private JpsMetrics() { super(PlatformScopesKt.JPS); }

  public static JpsMetrics getInstance() {
    return _instance.get();
  }

  public static final String jpsSyncSpanName = "jps.sync";
}
