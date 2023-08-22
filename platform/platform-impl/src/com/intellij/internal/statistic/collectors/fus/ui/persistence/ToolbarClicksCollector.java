// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui.persistence;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public class ToolbarClicksCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("toolbar", 61);
  private static final VarargEventId CLICKED = ActionsEventLogGroup.registerActionEvent(GROUP, "clicked");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void record(@NotNull AnAction action, String place, @NotNull InputEvent inputEvent, @NotNull DataContext dataContext) {
    AnActionEvent event = AnActionEvent.createFromInputEvent(
      inputEvent, place, null, dataContext, false, true);
    ActionsCollectorImpl.record(CLICKED, event.getProject(), action, event, (list) -> Unit.INSTANCE);
  }
}