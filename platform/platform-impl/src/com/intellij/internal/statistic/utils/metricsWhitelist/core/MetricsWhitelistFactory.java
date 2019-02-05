// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MetricsWhitelistFactory<T extends MetricsWhitelist> {
  @Nullable
  MetricsWhitelistHeader createHeader(@NotNull String rawHeader);

  @Nullable
  T createWhitelist(@NotNull String rawWhitelist);
}
