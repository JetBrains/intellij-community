// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.monitoring;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.VFSNeedsRebuildException.RebuildCause;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class VfsUsageCollector extends CounterUsagesCollector {
  private static final int DURATION_THRESHOLD_MS = 100;

  private static final EventLogGroup GROUP_VFS = new EventLogGroup("vfs", 7);


  /* ================== EVENT_INITIAL_REFRESH: ====================================================== */

  private static final LongEventField FIELD_WAIT_MS = EventFields.Long("wait_ms");  // -1 for synchronous refresh/events

  private static final EventId1<Long> EVENT_INITIAL_REFRESH = GROUP_VFS.registerEvent(
    "initial_refresh",
    EventFields.DurationMs
  );

  /* ================== EVENT_REFRESH_SESSION: ====================================================== */

  private static final BooleanEventField FIELD_REFRESH_RECURSIVE = EventFields.Boolean("recursive");
  private static final RoundedIntEventField FIELD_REFRESH_LOCAL_ROOTS = EventFields.RoundedInt("roots_local");
  private static final RoundedIntEventField FIELD_REFRESH_ARCHIVE_ROOTS = EventFields.RoundedInt("roots_arc");
  private static final RoundedIntEventField FIELD_REFRESH_OTHER_ROOTS = EventFields.RoundedInt("roots_other");
  private static final BooleanEventField FIELD_REFRESH_CANCELLED = EventFields.Boolean("cancelled");
  private static final IntEventField FIELD_REFRESH_TRIES = EventFields.Int("tries");
  private static final VarargEventId EVENT_REFRESH_SESSION = GROUP_VFS.registerVarargEvent(
    "refresh_session",
    FIELD_REFRESH_RECURSIVE, FIELD_REFRESH_LOCAL_ROOTS, FIELD_REFRESH_ARCHIVE_ROOTS, FIELD_REFRESH_OTHER_ROOTS,
    FIELD_REFRESH_CANCELLED, FIELD_WAIT_MS, EventFields.DurationMs, FIELD_REFRESH_TRIES
  );

  /* ================== EVENT_REFRESH_SCAN: ====================================================== */

  private static final IntEventField FIELD_REFRESH_FULL_SCANS = EventFields.Int("full_scans");
  private static final IntEventField FIELD_REFRESH_PARTIAL_SCANS = EventFields.Int("partial_scans");
  private static final IntEventField FIELD_REFRESH_RETRIES = EventFields.Int("retries");
  private static final LongEventField FIELD_REFRESH_VFS_TIME_MS = EventFields.Long("vfs_time_ms");
  private static final LongEventField FIELD_REFRESH_IO_TIME_MS = EventFields.Long("io_time_ms");
  private static final VarargEventId EVENT_REFRESH_SCAN = GROUP_VFS.registerVarargEvent(
    "refresh_scan",
    FIELD_REFRESH_FULL_SCANS, FIELD_REFRESH_PARTIAL_SCANS, FIELD_REFRESH_RETRIES, EventFields.DurationMs, FIELD_REFRESH_VFS_TIME_MS,
    FIELD_REFRESH_IO_TIME_MS
  );

  /* ================== EVENT_EVENTS: ====================================================== */

  private static final LongEventField FIELD_EVENT_LISTENERS_MS = EventFields.Long("listeners_ms");
  private static final IntEventField FIELD_EVENT_TRIES = EventFields.Int("tries");
  private static final IntEventField FIELD_EVENT_NUMBER = EventFields.Int("events");
  private static final VarargEventId EVENT_EVENTS = GROUP_VFS.registerVarargEvent(
    "events",
    FIELD_WAIT_MS, FIELD_EVENT_LISTENERS_MS, FIELD_EVENT_TRIES, EventFields.DurationMs, FIELD_EVENT_NUMBER
  );

  /* ================== EVENT_VFS_INITIALIZATION: ====================================================== */

  /** What causes VFS rebuild (if any) */
  private static final EnumEventField<RebuildCause> FIELD_REBUILD_CAUSE = EventFields.Enum("rebuild_cause", RebuildCause.class);
  /**
   * How many attempts to init VFS were made.
   * In regular caqse, it is only 1 attempt, but could be >1 if VFS was rebuilt.
   */
  private static final IntEventField FIELD_INITIALIZATION_ATTEMPTS = EventFields.Int("init_attempts");
  /** Timestamp current VFS was created & initialized */
  private static final LongEventField FIELD_CREATION_TIMESTAMP = EventFields.Long("creation_timestamp");
  /** Current VFS implementation version, see {@link FSRecords#getVersion()} */
  private static final IntEventField FIELD_IMPL_VERSION = EventFields.Int("impl_version");


  private static final VarargEventId EVENT_VFS_INITIALIZATION = GROUP_VFS.registerVarargEvent(
    "initialization",
    FIELD_REBUILD_CAUSE,
    FIELD_CREATION_TIMESTAMP,
    FIELD_INITIALIZATION_ATTEMPTS,
    FIELD_IMPL_VERSION
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP_VFS;
  }

  public static void logInitialRefresh(Project project, long duration) {
    EVENT_INITIAL_REFRESH.log(project, duration);
  }

  public static void logRefreshSession(boolean recursive,
                                       int lfsRoots,
                                       int arcRoots,
                                       int otherRoots,
                                       boolean cancelled,
                                       long wait,
                                       long duration,
                                       int tries) {
    if (duration >= DURATION_THRESHOLD_MS) {
      EVENT_REFRESH_SESSION.log(
        FIELD_REFRESH_RECURSIVE.with(recursive), FIELD_REFRESH_LOCAL_ROOTS.with(lfsRoots), FIELD_REFRESH_ARCHIVE_ROOTS.with(arcRoots),
        FIELD_REFRESH_OTHER_ROOTS.with(otherRoots),
        FIELD_REFRESH_CANCELLED.with(cancelled), FIELD_WAIT_MS.with(wait), EventFields.DurationMs.with(duration),
        FIELD_REFRESH_TRIES.with(tries));
    }
  }

  public static void logRefreshScan(int fullScans, int partialScans, int retries, long duration, long vfsTime, long ioTime) {
    if (duration >= DURATION_THRESHOLD_MS) {
      EVENT_REFRESH_SCAN.log(
        FIELD_REFRESH_FULL_SCANS.with(fullScans), FIELD_REFRESH_PARTIAL_SCANS.with(partialScans), FIELD_REFRESH_RETRIES.with(retries),
        EventFields.DurationMs.with(duration), FIELD_REFRESH_VFS_TIME_MS.with(vfsTime), FIELD_REFRESH_IO_TIME_MS.with(ioTime));
    }
  }

  public static void logEventProcessing(long wait, long listenerTime, int listenerTries, long edtTime, int events) {
    if (listenerTime + edtTime >= DURATION_THRESHOLD_MS) {
      EVENT_EVENTS.log(
        FIELD_WAIT_MS.with(wait), FIELD_EVENT_LISTENERS_MS.with(listenerTime), FIELD_EVENT_TRIES.with(listenerTries),
        EventFields.DurationMs.with(edtTime), FIELD_EVENT_NUMBER.with(events));
    }
  }

  public static void logVfsInitialization(int vfsImplementationVersion,
                                          long vfsCreationTimestamp,
                                          int initializationAttempts,
                                          @NotNull RebuildCause rebuildCause) {
    EVENT_VFS_INITIALIZATION.log(
      FIELD_REBUILD_CAUSE.with(rebuildCause),
      FIELD_CREATION_TIMESTAMP.with(vfsCreationTimestamp),
      FIELD_INITIALIZATION_ATTEMPTS.with(initializationAttempts),
      FIELD_IMPL_VERSION.with(vfsImplementationVersion)
    );
  }
}
