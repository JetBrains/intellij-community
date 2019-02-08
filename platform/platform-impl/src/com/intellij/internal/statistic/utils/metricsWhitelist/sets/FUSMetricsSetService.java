// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.sets;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface FUSMetricsSetService {
  static FUSMetricsSetService getInstance() {
    return ServiceManager.getService(FUSMetricsSetService.class);
  }

  boolean containsMetric(@NotNull String metricsSetId, @NotNull String metric);

  void preloadSets(@NotNull Set<String> ids);
}
