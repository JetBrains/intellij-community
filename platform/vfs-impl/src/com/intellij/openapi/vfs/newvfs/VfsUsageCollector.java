// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

final class VfsUsageCollector extends CounterUsagesCollector {
  private static final int DURATION_THRESHOLD_MS = 100;

  private static final EventLogGroup GROUP = new EventLogGroup("vfs", 4);

  private static final BooleanEventField RefreshRecursive = EventFields.Boolean("recursive");
  private static final IntEventField RefreshLocalRoots = EventFields.Int("roots_local");
  private static final IntEventField RefreshArchiveRoots = EventFields.Int("roots_arc");
  private static final IntEventField RefreshOtherRoots = EventFields.Int("roots_other");
  private static final BooleanEventField RefreshCancelled = EventFields.Boolean("cancelled");
  private static final LongEventField RefreshWaitMs = EventFields.Long("wait_ms");  // -1 for synchronous refresh
  private static final IntEventField RefreshTries = EventFields.Int("tries");
  private static final VarargEventId REFRESH_SESSION = GROUP.registerVarargEvent(
    "refresh_session",
    RefreshRecursive, RefreshLocalRoots, RefreshArchiveRoots, RefreshOtherRoots,
    RefreshCancelled, RefreshWaitMs, EventFields.DurationMs, RefreshTries);

  private static final IntEventField RefreshFullScans = EventFields.Int("full_scans");
  private static final IntEventField RefreshPartialScans = EventFields.Int("partial_scans");
  private static final IntEventField RefreshRetries = EventFields.Int("retries");
  private static final LongEventField RefreshVfsTimeMs = EventFields.Long("vfs_time_ms");
  private static final LongEventField RefreshIoTimeMs = EventFields.Long("io_time_ms");
  private static final VarargEventId REFRESH_SCAN = GROUP.registerVarargEvent(
    "refresh_scan",
    RefreshFullScans, RefreshPartialScans, RefreshRetries, EventFields.DurationMs, RefreshVfsTimeMs, RefreshIoTimeMs);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  static void logRefreshSession(boolean recursive, int lfsRoots, int arcRoots, int otherRoots, boolean cancelled, long wait, long duration, int tries) {
    if (duration >= DURATION_THRESHOLD_MS) {
      REFRESH_SESSION.log(
        RefreshRecursive.with(recursive), RefreshLocalRoots.with(lfsRoots), RefreshArchiveRoots.with(arcRoots), RefreshOtherRoots.with(otherRoots),
        RefreshCancelled.with(cancelled), RefreshWaitMs.with(wait), EventFields.DurationMs.with(duration), RefreshTries.with(tries));
    }
  }

  static void logRefreshScan(int fullScans, int partialScans, int retries, long duration, long vfsTime, long ioTime) {
    if (duration >= DURATION_THRESHOLD_MS) {
      REFRESH_SCAN.log(
        RefreshFullScans.with(fullScans), RefreshPartialScans.with(partialScans), RefreshRetries.with(retries),
        EventFields.DurationMs.with(duration), RefreshVfsTimeMs.with(vfsTime), RefreshIoTimeMs.with(ioTime));
    }
  }
}
