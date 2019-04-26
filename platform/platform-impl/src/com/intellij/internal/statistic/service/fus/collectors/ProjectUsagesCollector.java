// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @see ApplicationUsagesCollector
 */
public abstract class ProjectUsagesCollector extends FeatureUsagesCollector {
  private static final ExtensionPointName<ProjectUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.projectUsagesCollector");

  @NotNull
  public static Set<ProjectUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  @NotNull
  public abstract Set<UsageDescriptor> getUsages(@NotNull Project project);

  /**
   * @deprecated use {@link ProjectUsagesCollector#getData(Project)}
   */
  @Nullable
  @Deprecated
  public FUSUsageContext getContext(@NotNull Project project) {
    return null;
  }

  @Nullable
  public FeatureUsageData getData(@NotNull Project project) {
    final FUSUsageContext context = getContext(project);
    return context != null ? new FeatureUsageData().addFeatureContext(context) : null;
  }
}