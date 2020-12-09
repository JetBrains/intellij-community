// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface FusAwareAction {
  @NotNull List<EventPair<?>> getAdditionalUsageData(@NotNull AnActionEvent event);
}
