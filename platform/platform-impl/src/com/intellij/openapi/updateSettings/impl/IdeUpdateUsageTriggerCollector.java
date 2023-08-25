// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class IdeUpdateUsageTriggerCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ide.self.update", 4);

  private static final EventId1<String> DIALOG_SHOWN = GROUP.registerEvent(
    "dialog.shown", EventFields.String("patches", List.of("not.available", "manual", "auto")));

  private static final EventId1<Boolean> UPDATE_WHATS_NEW = GROUP.registerEvent(
    "update.whats.new", EventFields.Boolean("show_in_editor"));

  static final EventId NOTIFICATION_SHOWN = GROUP.registerEvent("notification.shown");

  static final EventId NOTIFICATION_CLICKED = GROUP.registerEvent("notification.clicked");

  static final EventId UPDATE_FAILED = GROUP.registerEvent("update.failed");

  static final EventId UPDATE_STARTED = GROUP.registerEvent("dialog.update.started");

  static final EventId MANUAL_PATCH_PREPARED = GROUP.registerEvent("dialog.manual.patch.prepared");

  static void triggerUpdateDialog(@Nullable UpdateChain patches, boolean isRestartCapable) {
    var patchesValue = patches == null ? "not.available" : !isRestartCapable ? "manual" : "auto";
    DIALOG_SHOWN.log(patchesValue);
  }

  static void majorUpdateHappened(boolean showInEditor) {
    UPDATE_WHATS_NEW.log(showInEditor);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
