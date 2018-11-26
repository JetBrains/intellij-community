// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;


public class KeymapUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.keymaps.name";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null)
      return Collections.emptySet();

    Keymap keymap = keymapManager.getActiveKeymap();
    String keymapName;
    if (keymap.canModify()) {
      Keymap parent = keymap.getParent();
      if (parent != null && !parent.canModify()) {
        keymapName = "Custom (Based on " + parent.getName() + " keymap)";
      } else {
        keymapName = "Custom (Based on unknown)";
      }
    } else {
      keymapName = keymap.getName();
    }
    return Collections.singleton(new UsageDescriptor(ensureProperKey(keymapName)));
  }
}
