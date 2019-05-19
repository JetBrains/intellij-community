// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @see ProjectUsagesCollector
 */
public abstract class ApplicationUsagesCollector extends FeatureUsagesCollector {
  private static final ExtensionPointName<ApplicationUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.applicationUsagesCollector");

  @NotNull
  public static Set<ApplicationUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  @NotNull
  public abstract Set<UsageDescriptor> getUsages();

  /**
   * @deprecated use {@link ApplicationUsagesCollector#getData()}
   */
  @Deprecated
  @Nullable
  public FUSUsageContext getContext() {
    return null;
  }

  @Nullable
  public FeatureUsageData getData() {
    final FUSUsageContext context = getContext();
    return context != null ? new FeatureUsageData().addFeatureContext(context) : null;
  }
}