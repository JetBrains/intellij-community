// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.EventRateThrottleResult;
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TypingEventsLogger extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("editor.typing", 3);

  private static final EventId TYPED = GROUP.registerEvent("typed");
  private static final EventId TOO_MANY_EVENTS = GROUP.registerEvent("too.many.events");

  private static final EventsRateWindowThrottle ourThrottle =
    new EventsRateWindowThrottle(8000, 60 * 60 * 1000, System.currentTimeMillis());

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private static void logTypingEvent(@NotNull DataContext dataContext, @NotNull EventId eventId) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    eventId.log(project);
  }

  public static class TypingEventsListener implements AnActionListener {
    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      EventRateThrottleResult result = ourThrottle.tryPass(System.currentTimeMillis());
      if (result == EventRateThrottleResult.ACCEPT) {
        logTypingEvent(dataContext, TYPED);
      }
      else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
        logTypingEvent(dataContext, TOO_MANY_EVENTS);
      }
    }
  }
}
