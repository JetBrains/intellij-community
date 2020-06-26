// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules;

import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class EventContext {
   public final String eventId;
   public final Map<String, Object>  eventData;
   public PluginInfo pluginInfo;

  private EventContext(@NotNull String eventId, @NotNull Map<String, Object> eventData) {
    this.eventId = eventId;
    this.eventData = ContainerUtil.unmodifiableOrEmptyMap(eventData);
    this.pluginInfo = null;
  }

  public static EventContext create(@NotNull  String eventId, @NotNull  Map<String, Object>  eventData) {
     return new EventContext(eventId, eventData);
  }

  public void setPluginInfo(@NotNull PluginInfo info) {
    this.pluginInfo = info;
  }
}
