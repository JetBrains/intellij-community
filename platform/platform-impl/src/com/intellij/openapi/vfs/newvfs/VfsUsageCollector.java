// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;

public final class VfsUsageCollector extends CounterUsagesCollector {
  private static final int DURATION_THRESHOLD_MS = 100;

  private static final EventLogGroup GROUP = new EventLogGroup("vfs", 6);

  private static final LongEventField WaitMs = EventFields.Long("wait_ms");  // -1 for synchronous refresh/events

  private static final EventId1<Long> INITIAL_REFRESH = GROUP.registerEvent("initial_refresh", EventFields.DurationMs);

  private static final BooleanEventField RefreshRecursive = EventFields.Boolean("recursive");
  private static final RoundedIntEventField RefreshLocalRoots = EventFields.RoundedInt("roots_local");
  private static final RoundedIntEventField RefreshArchiveRoots = EventFields.RoundedInt("roots_arc");
  private static final RoundedIntEventField RefreshOtherRoots = EventFields.RoundedInt("roots_other");
  private static final BooleanEventField RefreshCancelled = EventFields.Boolean("cancelled");
  private static final IntEventField RefreshTries = EventFields.Int("tries");
  private static final VarargEventId REFRESH_SESSION = GROUP.registerVarargEvent(
    "refresh_session",
    RefreshRecursive, RefreshLocalRoots, RefreshArchiveRoots, RefreshOtherRoots,
    RefreshCancelled, WaitMs, EventFields.DurationMs, RefreshTries);

  private static final IntEventField RefreshFullScans = EventFields.Int("full_scans");
  private static final IntEventField RefreshPartialScans = EventFields.Int("partial_scans");
  private static final IntEventField RefreshRetries = EventFields.Int("retries");
  private static final LongEventField RefreshVfsTimeMs = EventFields.Long("vfs_time_ms");
  private static final LongEventField RefreshIoTimeMs = EventFields.Long("io_time_ms");
  private static final VarargEventId REFRESH_SCAN = GROUP.registerVarargEvent(
    "refresh_scan",
    RefreshFullScans, RefreshPartialScans, RefreshRetries, EventFields.DurationMs, RefreshVfsTimeMs, RefreshIoTimeMs);

  private static final LongEventField EventListenersMs = EventFields.Long("listeners_ms");
  private static final IntEventField EventTries = EventFields.Int("tries");
  private static final IntEventField EventNumber = EventFields.Int("events");
  private static final VarargEventId EVENTS = GROUP.registerVarargEvent(
    "events",
    WaitMs, EventListenersMs, EventTries, EventFields.DurationMs, EventNumber);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logInitialRefresh(Project project, long duration) {
    INITIAL_REFRESH.log(project, duration);
  }

  static void logRefreshSession(boolean recursive, int lfsRoots, int arcRoots, int otherRoots, boolean cancelled, long wait, long duration, int tries) {
    if (duration >= DURATION_THRESHOLD_MS) {
      REFRESH_SESSION.log(
        RefreshRecursive.with(recursive), RefreshLocalRoots.with(lfsRoots), RefreshArchiveRoots.with(arcRoots), RefreshOtherRoots.with(otherRoots),
        RefreshCancelled.with(cancelled), WaitMs.with(wait), EventFields.DurationMs.with(duration), RefreshTries.with(tries));
    }
  }

  static void logRefreshScan(int fullScans, int partialScans, int retries, long duration, long vfsTime, long ioTime) {
    if (duration >= DURATION_THRESHOLD_MS) {
      REFRESH_SCAN.log(
        RefreshFullScans.with(fullScans), RefreshPartialScans.with(partialScans), RefreshRetries.with(retries),
        EventFields.DurationMs.with(duration), RefreshVfsTimeMs.with(vfsTime), RefreshIoTimeMs.with(ioTime));
    }
  }

  static void logEventProcessing(long wait, long listenerTime, int listenerTries, long edtTime, int events) {
    if (listenerTime + edtTime >= DURATION_THRESHOLD_MS) {
      EVENTS.log(
        WaitMs.with(wait), EventListenersMs.with(listenerTime), EventTries.with(listenerTries),
        EventFields.DurationMs.with(edtTime), EventNumber.with(events));
    }
  }
}
