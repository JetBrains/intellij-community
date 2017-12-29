// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.customUsageCollectors;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.Experiments;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public class ExperimentalFeaturesUsageCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP_ID = GroupDescriptor.create("Experimental Features");

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return Arrays.stream(Experiments.EP_NAME.getExtensions())
      .filter(f -> Experiments.isFeatureEnabled(f.id))
      .map(f -> new UsageDescriptor(f.id))
      .collect(Collectors.toSet());
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP_ID;
  }
}
