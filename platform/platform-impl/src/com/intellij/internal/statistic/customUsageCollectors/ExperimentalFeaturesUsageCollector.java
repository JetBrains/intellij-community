// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.customUsageCollectors;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class ExperimentalFeaturesUsageCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP_ID = GroupDescriptor.create("Experimental Features");

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    Set<UsageDescriptor> usages = new THashSet<>();
    for (ExperimentalFeature feature : Experiments.EP_NAME.getExtensions()) {
      usages.add(new UsageDescriptor(feature.id, Experiments.isFeatureEnabled(feature.id) ? 1 : 0));
    }
    return usages;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP_ID;
  }
}
