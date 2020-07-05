// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.EventRateThrottleResult;
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

public class TypingEventsLogger implements CommandListener {
  public static final String GROUP_ID = "editor.typing";

  public static final EventsRateWindowThrottle ourThrottle =
    new EventsRateWindowThrottle(500, 60 * 60 * 1000, System.currentTimeMillis());

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    if (isTypingEvent(event)) {
      EventRateThrottleResult result = ourThrottle.tryPass(System.currentTimeMillis());
      if (result == EventRateThrottleResult.ACCEPT) {
        FUCounterUsageLogger.getInstance().logEvent(event.getProject(), GROUP_ID, "typed");
      }
      else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
        FUCounterUsageLogger.getInstance().logEvent(event.getProject(), GROUP_ID, "too.many.events");
      }
    }
  }

  boolean isTypingEvent(@NotNull CommandEvent event) {
    if (event.getCommand() == EmptyRunnable.INSTANCE) {
      return EditorBundle.message("typing.in.editor.command.name").equals(event.getCommandName());
    }
    return false;
  }
}
