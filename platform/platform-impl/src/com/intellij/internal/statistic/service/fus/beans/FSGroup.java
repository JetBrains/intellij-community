// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FSGroup {

  public String id;
  public Map<String, Integer> metrics ;

  private FSGroup(String id, Set<UsageDescriptor> usages) {
    this.id = id;
    for (UsageDescriptor usage : usages) {
      getMetrics().put(UsageDescriptorKeyValidator.replaceForbiddenSymbols(usage.getKey()), usage.getValue());
    }
  }

  @NotNull
  public Map<String, Integer> getMetrics() {
    if (metrics == null) {
      metrics = ContainerUtil.newHashMap();
    }
    return metrics;
  }

  public static FSGroup create(@NotNull String groupId, @NotNull Set<UsageDescriptor> usages) {
     return new FSGroup(groupId, usages);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FSGroup group = (FSGroup)o;
    return Objects.equals(id, group.id) &&
           Objects.equals(metrics, group.metrics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, metrics);
  }
}
