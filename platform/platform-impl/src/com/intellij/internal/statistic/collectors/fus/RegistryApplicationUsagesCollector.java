// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


public class RegistryApplicationUsagesCollector extends ApplicationUsagesCollector {
  private static final String GROUP_ID = "statistics.platform.registry.application";

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getChangedValuesUsages();
  }

  @NotNull
  static Set<UsageDescriptor> getChangedValuesUsages() {
    Set<UsageDescriptor> registry = Registry.getAll().stream()
      .filter(key -> key.isChangedFromDefault())
      .map(key -> new UsageDescriptor(key.getKey()))
      .collect(Collectors.toSet());

    Set<UsageDescriptor> experiments = Arrays.stream(Experiments.EP_NAME.getExtensions())
      .filter(f -> Experiments.isFeatureEnabled(f.id))
      .map(f -> new UsageDescriptor(f.id))
      .collect(Collectors.toSet());

    HashSet<UsageDescriptor> result = new HashSet<>(registry);
    result.addAll(experiments);
    return result;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return GROUP_ID;
  }
}
