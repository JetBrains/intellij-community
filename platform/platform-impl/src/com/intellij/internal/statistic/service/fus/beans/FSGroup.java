// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FSGroup extends FSContextProvider {

  public String id;
  public Set<FSMetric> metrics;

  private FSGroup(CollectorGroupDescriptor groupDescriptor, Set<UsageDescriptor> usages) {
    super(groupDescriptor.getContext());

    this.id = groupDescriptor.getGroupID();
    for (UsageDescriptor usage : usages) {
      getMetrics().add(FSMetric.create(usage));
    }
  }

  @NotNull
  public Set<FSMetric> getMetrics() {
    if (metrics == null) {
      metrics = ContainerUtil.newLinkedHashSet();
    }
    return metrics;
  }

  public static FSGroup create(@NotNull CollectorGroupDescriptor groupId, @NotNull Set<UsageDescriptor> usages) {
    return new FSGroup(groupId, usages);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FSGroup)) return false;
    FSGroup group = (FSGroup)o;
    return Objects.equals(id, group.id) &&
           Objects.equals(metrics, group.metrics) &&
           Objects.equals(context, group.context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, metrics, context);
  }
}
