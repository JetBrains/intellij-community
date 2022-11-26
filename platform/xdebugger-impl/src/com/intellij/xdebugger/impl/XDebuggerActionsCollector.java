// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

import java.util.List;

public class XDebuggerActionsCollector extends CounterUsagesCollector {
  private static final EventLogGroup group = new EventLogGroup("xdebugger.actions", 1);

  public static final String PLACE_THREADS_VIEW = "threadsView";
  public static final String PLACE_FRAMES_VIEW = "framesView";

  public static final EventId1<String> threadSelected =
    group.registerEvent("thread.selected", EventFields.String("place", List.of(PLACE_FRAMES_VIEW, PLACE_THREADS_VIEW)));
  public static final EventId1<String> frameSelected =
    group.registerEvent("frame.selected", EventFields.String("place", List.of(PLACE_FRAMES_VIEW, PLACE_THREADS_VIEW)));
  public static final EventId sessionChanged = group.registerEvent("session.selected");

  @Override
  public EventLogGroup getGroup() {
    return group;
  }
}
