// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.customUsageCollectors;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryUsagesCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP_ID = GroupDescriptor.create("Registry");

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return Registry.getAll().stream()
      .filter(key -> key.isChangedFromDefault())
      .map(key -> new UsageDescriptor(key.getKey()))
      .collect(Collectors.toSet());
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP_ID;
  }
}
