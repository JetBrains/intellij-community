// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;


public class KeymapUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "keymaps.name";
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) return Collections.emptySet();

    Keymap keymap = keymapManager.getActiveKeymap();
    FeatureUsageData data = new FeatureUsageData().
      addData("keymap_name", getKeymapName(keymap));

    if (keymap.canModify()) {
      data.addData("based_on", getKeymapName(keymap.getParent()));
    }
    return Collections.singleton(MetricEventFactoryKt.newMetric("ide.keymap", data));
  }

  @NotNull
  private static String getKeymapName(@Nullable Keymap keymap) {
    if (keymap == null) return "unknown";
    return keymap.canModify() ? "custom" : keymap.getName();
  }
}
