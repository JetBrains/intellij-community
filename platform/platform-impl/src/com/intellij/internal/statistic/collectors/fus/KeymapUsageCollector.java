// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


@ApiStatus.Internal
public final class KeymapUsageCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("keymaps.name", 2);
  private static final StringEventField KEYMAP_NAME = EventFields.StringValidatedByEnum("keymap_name", "keymaps");
  private static final StringEventField BASED_ON = EventFields.StringValidatedByEnum("based_on", "keymaps");
  private static final VarargEventId KEYMAP = GROUP.registerVarargEvent("ide.keymap", KEYMAP_NAME, BASED_ON);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) return Collections.emptySet();

    Keymap keymap = keymapManager.getActiveKeymap();
    List<EventPair<String>> data = new ArrayList<>();
    data.add(KEYMAP_NAME.with(getKeymapName(keymap)));

    if (keymap.canModify()) {
      data.add(BASED_ON.with(getKeymapName(keymap.getParent())));
    }
    return Collections.singleton(KEYMAP.metric(data));
  }

  private static @NotNull String getKeymapName(@Nullable Keymap keymap) {
    if (keymap == null) return "unknown";
    return keymap.canModify() ? "custom" : keymap.getName();
  }
}
