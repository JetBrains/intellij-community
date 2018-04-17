// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.productivity;

import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.ProductivityFeaturesRegistry;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@Deprecated // to be removed in 2018.2
public class LegacyProductivityFeaturesUsageCollector extends UsagesCollector {

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("productivity",  GroupDescriptor.LOWER_PRIORITY);
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    Set<UsageDescriptor> usages = new HashSet<>();

    final ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    for (String featureId : registry.getFeatureIds()) {
      final FeatureDescriptor featureDescriptor = registry.getFeatureDescriptor(featureId);
      if (featureDescriptor != null) {
        usages.add(new UsageDescriptor(featureId, featureDescriptor.getUsageCount()));
      }
    }

    return usages;
  }
}
