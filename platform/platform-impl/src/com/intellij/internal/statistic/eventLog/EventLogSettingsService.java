// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EventLogSettingsService {

  @Nullable
  String getServiceUrl();

  int getPermittedTraffic();

  @NotNull
  LogEventFilter getEventFilter();
}
