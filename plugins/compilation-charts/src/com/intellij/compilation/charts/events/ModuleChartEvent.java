// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.events;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface ModuleChartEvent extends ChartEvent {
  @NotNull @NlsContexts.Label
  String name();
  @NotNull String type();
  boolean isTest();
  long threadId();
}
