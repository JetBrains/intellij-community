// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core.cache;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MetricsWhitelistCache<T extends MetricsWhitelist> {
  @Nullable
  T get(@NotNull String whitelistId);
}
