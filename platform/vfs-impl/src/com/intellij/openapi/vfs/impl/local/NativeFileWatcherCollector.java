// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

final class NativeFileWatcherCollector extends CounterUsagesCollector {
  private final static EventLogGroup GROUP = new EventLogGroup("vfs.watcher", 1);

  private static final EventId CANNOT_START = GROUP.registerEvent("cannot_start");
  private static final EventId GIVEN_UP = GROUP.registerEvent("given_up");
  private static final EventId1<Integer> RESTART = GROUP.registerEvent("restart", EventFields.Int("attempt"));
  private static final EventId DISABLED = GROUP.registerEvent("disabled");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  static void cannotStart() {
    CANNOT_START.log();
  }

  static void givenUp() {
    GIVEN_UP.log();
  }

  static void restart(int attempt) {
    RESTART.log(attempt);
  }

  static void disabled() {
    DISABLED.log();
  }
}
