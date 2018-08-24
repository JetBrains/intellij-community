// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * See example:
 * <pre>{@code
 * public final class MyApplicationActionsUsageTriggerCollector extends ApplicationUsageTriggerCollector {
 *   public static void record(@NotNull String metric) {
 *     FUSApplicationUsageTrigger.getInstance().trigger(MyApplicationActionsUsageTriggerCollector.class, metric);
 *   }
 *
 *   public String getGroupId() {
 *     return "statistics.my.application.actions";
 *   }
 * }
 * }</pre>
 * In any place of code write: {@code MyApplicationActionsUsageTriggerCollector.record("my.cool.action.performed");}
 */
public abstract class ApplicationUsageTriggerCollector extends ApplicationUsagesCollector implements FUStatisticsDifferenceSender {
  @NotNull
  @Override
  public final Set<UsageDescriptor> getUsages() {
    return FUSApplicationUsageTrigger.getInstance().getData(getGroupId());
  }

  @Nullable
  public final FUSUsageContext getContext() {return null;}
}
