// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.events;

import org.jetbrains.annotations.NotNull;

public record ModuleFinishChartEvent(@NotNull String name,
                                     @NotNull String type,
                                     boolean isTest,
                                     boolean isFileBased,
                                     long nanoTime,
                                     long threadId) implements ModuleChartEvent{
}
