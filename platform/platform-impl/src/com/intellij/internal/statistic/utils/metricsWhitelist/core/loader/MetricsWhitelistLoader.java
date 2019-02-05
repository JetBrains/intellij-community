// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core.loader;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import org.jetbrains.annotations.NotNull;

public interface MetricsWhitelistLoader<T extends MetricsWhitelist> {
  @NotNull
  MetricsWhitelistHeader loadHeader(@NotNull String id) throws Exception;

  @NotNull
  T loadWhitelist(@NotNull String id) throws Exception;
}
