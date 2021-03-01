// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface EventLogConfigOptionsListener {
  void optionsChanged(@NotNull String recorderId, @NotNull Map<String, String> options);
}
