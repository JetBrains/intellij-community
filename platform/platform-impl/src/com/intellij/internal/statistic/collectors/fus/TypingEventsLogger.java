// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.EventRateThrottleResult;
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TypingEventsLogger implements AnActionListener {
  public static final String GROUP_ID = "editor.typing";

  public static final EventsRateWindowThrottle ourThrottle =
    new EventsRateWindowThrottle(500, 60 * 60 * 1000, System.currentTimeMillis());

  @Override
  public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
    EventRateThrottleResult result = ourThrottle.tryPass(System.currentTimeMillis());
    if (result == EventRateThrottleResult.ACCEPT) {
      logTypingEvent(dataContext, "typed");
    }
    else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
      logTypingEvent(dataContext, "too.many.events");
    }
  }

  private static void logTypingEvent(@NotNull DataContext dataContext, @NotNull String eventId) {
    Project project = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, eventId);
  }
}
