// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;


public class KeymapUsageCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.keymap.name";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    KeymapManager keymapManager = KeymapManager.getInstance();
    return keymapManager != null
      ?Collections.singleton(new UsageDescriptor(ensureProperKey(keymapManager.getActiveKeymap().getName()))) 
      : Collections.emptySet();

  }
}
