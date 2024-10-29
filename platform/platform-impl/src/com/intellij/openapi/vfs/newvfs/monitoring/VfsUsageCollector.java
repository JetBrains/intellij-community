// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.monitoring;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.monitoring.VFSInitializationConditionsToFusReporter.VFSInitKind;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

import static com.intellij.internal.statistic.eventLog.events.EventFields.*;

@ApiStatus.Internal
public final class VfsUsageCollector extends CounterUsagesCollector {
  private static final int DURATION_THRESHOLD_MS = 100;

  private static final EventLogGroup GROUP_VFS = new EventLogGroup("vfs", 18);

  private static final LongEventField FIELD_WAIT_MS = Long("wait_ms");  // -1 for synchronous refresh/events

  /* ================== EVENT_INITIAL_REFRESH: ====================================================== */

  private static final EventId1<Long> EVENT_INITIAL_REFRESH = GROUP_VFS.registerEvent("initial_refresh", DurationMs);

  /* ================== EVENT_BACKGROUND_REFRESH: ====================================================== */

  private static final RoundedIntEventField FIELD_BG_REFRESH_SESSIONS = RoundedInt("sessions");
  private static final RoundedIntEventField FIELD_BG_REFRESH_EVENTS = RoundedInt("events");

  private static final EventId3<Long, Integer, Integer> EVENT_BACKGROUND_REFRESH = GROUP_VFS.registerEvent(
    "background_refresh", DurationMs, FIELD_BG_REFRESH_SESSIONS, FIELD_BG_REFRESH_EVENTS);

  /* ================== EVENT_REFRESH_SESSION: ====================================================== */

  private static final BooleanEventField FIELD_REFRESH_RECURSIVE = Boolean("recursive");
  private static final RoundedIntEventField FIELD_REFRESH_LOCAL_ROOTS = RoundedInt("roots_local");
  private static final RoundedIntEventField FIELD_REFRESH_ARCHIVE_ROOTS = RoundedInt("roots_arc");
  private static final RoundedIntEventField FIELD_REFRESH_OTHER_ROOTS = RoundedInt("roots_other");
  private static final BooleanEventField FIELD_REFRESH_CANCELLED = Boolean("cancelled");
  private static final IntEventField FIELD_REFRESH_TRIES = Int("tries");

  private static final VarargEventId EVENT_REFRESH_SESSION = GROUP_VFS.registerVarargEvent(
    "refresh_session",
    FIELD_REFRESH_RECURSIVE, FIELD_REFRESH_LOCAL_ROOTS, FIELD_REFRESH_ARCHIVE_ROOTS, FIELD_REFRESH_OTHER_ROOTS,
    FIELD_REFRESH_CANCELLED, FIELD_WAIT_MS, DurationMs, FIELD_REFRESH_TRIES
  );

  /* ================== EVENT_REFRESH_SCAN: ====================================================== */

  private static final IntEventField FIELD_REFRESH_FULL_SCANS = Int("full_scans");
  private static final IntEventField FIELD_REFRESH_PARTIAL_SCANS = Int("partial_scans");
  private static final IntEventField FIELD_REFRESH_RETRIES = Int("retries");
  private static final LongEventField FIELD_REFRESH_VFS_TIME_MS = Long("vfs_time_ms");
  private static final LongEventField FIELD_REFRESH_IO_TIME_MS = Long("io_time_ms");

  private static final VarargEventId EVENT_REFRESH_SCAN = GROUP_VFS.registerVarargEvent(
    "refresh_scan",
    FIELD_REFRESH_FULL_SCANS, FIELD_REFRESH_PARTIAL_SCANS, FIELD_REFRESH_RETRIES, DurationMs, FIELD_REFRESH_VFS_TIME_MS,
    FIELD_REFRESH_IO_TIME_MS
  );

  /* ================== EVENT_EVENTS: ====================================================== */

  private static final LongEventField FIELD_EVENT_LISTENERS_MS = Long("listeners_ms");
  private static final IntEventField FIELD_EVENT_TRIES = Int("tries");
  private static final IntEventField FIELD_EVENT_NUMBER = Int("events");

  private static final VarargEventId EVENT_EVENTS = GROUP_VFS.registerVarargEvent(
    "events",
    FIELD_WAIT_MS, FIELD_EVENT_LISTENERS_MS, FIELD_EVENT_TRIES, DurationMs, FIELD_EVENT_NUMBER
  );

  /* ================== EVENT_VFS_INITIALIZATION: ====================================================== */

  /** What causes VFS rebuild (if any) */
  private static final EnumEventField<VFSInitKind> FIELD_INITIALIZATION_KIND = Enum("init_kind", VFSInitKind.class);
  /** A number of attempts to init VFS. Usually =1, but could be more if VFS was rebuilt. */
  private static final IntEventField FIELD_INITIALIZATION_ATTEMPTS = Int("init_attempts");
  /** Timestamp current VFS was created & initialized (ms, unix origin) */
  private static final LongEventField FIELD_CREATION_TIMESTAMP = Long("creation_timestamp");
  /** Current VFS implementation version, see {@link com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl#getVersion()} */
  private static final IntEventField FIELD_IMPL_VERSION = Int("impl_version");
  private static final LongEventField FIELD_TOTAL_INIT_DURATION_MS = Long("init_duration_ms");
  private static final StringListEventField FIELD_ERRORS_HAPPENED = StringList(
    "errors_happened",
    Stream.of(VFSInitException.ErrorCategory.values()).map(Enum::name).toList()
  );

  private static final VarargEventId EVENT_VFS_INITIALIZATION = GROUP_VFS.registerVarargEvent(
    "initialization",
    FIELD_INITIALIZATION_KIND, FIELD_CREATION_TIMESTAMP, FIELD_INITIALIZATION_ATTEMPTS, FIELD_IMPL_VERSION, FIELD_TOTAL_INIT_DURATION_MS,
    FIELD_ERRORS_HAPPENED
  );

  /* ================== EVENT_VFS_HEALTH_CHECK: ====================================================== */

  /** ={@link FSRecords#getCreationTimestamp()} */
  private static final LongEventField FIELD_HEALTH_CHECK_VFS_CREATION_TIMESTAMP_MS = Long("vfs_creation_timestamp_ms");
  /** How long the check have taken */
  private static final LongEventField FIELD_HEALTH_CHECK_DURATION_MS = Long("check_duration_ms");

  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_CHECKED = Int("file_records_checked");
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_DELETED = Int("file_records_deleted");
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_NAME_NULL = Int("file_records_name_null");
  /** Number of file-records there nameId can't be resolved against NamesEnumerator */
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_NAME_UNRESOLVABLE = Int("file_records_name_unresolvable");
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_ATTRIBUTE_ID_UNRESOLVABLE = Int("file_records_attribute_unresolvable");
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_CONTENT_NOT_NULL = Int("file_records_content_not_null");
  /** Number of file-records there contentId can't be resolved against NamesEnumerator */
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_CONTENT_UNRESOLVABLE = Int("file_records_content_unresolvable");
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_NULL_PARENTS = Int("file_records_null_parents");
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_CHILDREN_CHECKED = Int("file_records_children_checked");
  /** Number of file-records with children.parent != this */
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_CHILDREN_INCONSISTENT = Int("file_records_children_inconsistent");
  /** Total errors in file-records */
  private static final IntEventField FIELD_HEALTH_CHECK_FILE_RECORDS_GENERAL_ERRORS = Int("file_records_general_errors");

  /** Total records from NamesEnumerator checked */
  private static final IntEventField FIELD_HEALTH_CHECK_NAMES_CHECKED = Int("names_checked");
  /** Number of names from enumerator that doesn't resolve against the same enumerator */
  private static final IntEventField FIELD_HEALTH_CHECK_NAMES_RESOLVED_TO_NULL = Int("names_resolved_to_null");
  /** Number of ids from enumerator that doesn't resolve against the same enumerator */
  private static final IntEventField FIELD_HEALTH_CHECK_NAMES_IDS_RESOLVED_TO_NULL = Int("names_ids_resolved_to_null");
  /** Number of (name -> id -> name') resolutions there (name!=name') */
  private static final IntEventField FIELD_HEALTH_CHECK_NAMES_INCONSISTENT_RESOLUTIONS = Int("names_inconsistent_resolution");
  /** General errors in a name resolution */
  private static final IntEventField FIELD_HEALTH_CHECK_NAMES_GENERAL_ERRORS = Int("names_general_errors");

  private static final IntEventField FIELD_HEALTH_CHECK_CONTENTS_CHECKED = Int("contents_checked");
  private static final IntEventField FIELD_HEALTH_CHECK_CONTENTS_ERRORS = Int("contents_errors");

  private static final IntEventField FIELD_HEALTH_CHECK_ROOTS_CHECKED = Int("roots_checked");
  private static final IntEventField FIELD_HEALTH_CHECK_ROOTS_WITH_PARENTS = Int("roots_with_parents");
  /** root.isDeleted=true, but record is still in ROOTS catalog */
  private static final IntEventField FIELD_HEALTH_CHECK_ROOTS_DELETED_BUT_NOT_REMOVED = Int("roots_deleted_but_not_removed");
  private static final IntEventField FIELD_HEALTH_CHECK_ROOTS_ERRORS = Int("roots_errors");

  private static final IntEventField FIELD_HEALTH_CHECK_ATTRIBUTES_ERRORS = Int("attributes_errors");

  private static final VarargEventId EVENT_VFS_HEALTH_CHECK = GROUP_VFS.registerVarargEvent(
    "health_check",
    FIELD_HEALTH_CHECK_VFS_CREATION_TIMESTAMP_MS,

    FIELD_HEALTH_CHECK_DURATION_MS,

    FIELD_HEALTH_CHECK_FILE_RECORDS_CHECKED,
    FIELD_HEALTH_CHECK_FILE_RECORDS_DELETED,
    FIELD_HEALTH_CHECK_FILE_RECORDS_NAME_NULL,
    FIELD_HEALTH_CHECK_FILE_RECORDS_NAME_UNRESOLVABLE,
    FIELD_HEALTH_CHECK_FILE_RECORDS_ATTRIBUTE_ID_UNRESOLVABLE,
    FIELD_HEALTH_CHECK_FILE_RECORDS_CONTENT_NOT_NULL,
    FIELD_HEALTH_CHECK_FILE_RECORDS_CONTENT_UNRESOLVABLE,
    FIELD_HEALTH_CHECK_FILE_RECORDS_NULL_PARENTS,
    FIELD_HEALTH_CHECK_FILE_RECORDS_CHILDREN_CHECKED,
    FIELD_HEALTH_CHECK_FILE_RECORDS_CHILDREN_INCONSISTENT,
    FIELD_HEALTH_CHECK_FILE_RECORDS_GENERAL_ERRORS,

    FIELD_HEALTH_CHECK_NAMES_CHECKED,
    FIELD_HEALTH_CHECK_NAMES_IDS_RESOLVED_TO_NULL,
    FIELD_HEALTH_CHECK_NAMES_RESOLVED_TO_NULL,
    FIELD_HEALTH_CHECK_NAMES_INCONSISTENT_RESOLUTIONS,
    FIELD_HEALTH_CHECK_NAMES_GENERAL_ERRORS,

    FIELD_HEALTH_CHECK_CONTENTS_CHECKED,
    FIELD_HEALTH_CHECK_CONTENTS_ERRORS,

    FIELD_HEALTH_CHECK_ROOTS_CHECKED,
    FIELD_HEALTH_CHECK_ROOTS_WITH_PARENTS,
    FIELD_HEALTH_CHECK_ROOTS_DELETED_BUT_NOT_REMOVED,
    FIELD_HEALTH_CHECK_ROOTS_ERRORS,

    FIELD_HEALTH_CHECK_ATTRIBUTES_ERRORS
  );

  /* ================== EVENT_VFS_ACCUMULATED_ERRORS: ====================================================== */

  /**
   * Not any errors, but errors that are likely internal VFS errors, i.e., corruptions or code bugs.
   * E.g., error due to illegal argument passed from the outside is not counted.
   */
  private static final IntEventField FIELD_ACCUMULATED_VFS_ERRORS = Int("accumulated_errors");
  private static final LongEventField FIELD_TIME_SINCE_STARTUP = Long("time_since_startup_ms");

  private static final VarargEventId EVENT_VFS_INTERNAL_ERRORS = GROUP_VFS.registerVarargEvent(
    "internal_errors",
    FIELD_HEALTH_CHECK_VFS_CREATION_TIMESTAMP_MS,
    FIELD_TIME_SINCE_STARTUP,
    FIELD_ACCUMULATED_VFS_ERRORS
  );

  /* ==================================================================================================== */


  @Override
  public EventLogGroup getGroup() {
    return GROUP_VFS;
  }

  public static void logInitialRefresh(Project project, long duration) {
    EVENT_INITIAL_REFRESH.log(project, duration);
  }

  public static void logBackgroundRefresh(long duration, int sessions, int events) {
    if (duration > DURATION_THRESHOLD_MS) {
      EVENT_BACKGROUND_REFRESH.log(duration, sessions, events);
    }
  }

  public static void logRefreshSession(boolean recursive, int lfsRoots, int arcRoots, int otherRoots, boolean cancelled,
                                       long wait, long duration, int tries) {
    if (duration >= DURATION_THRESHOLD_MS) {
      EVENT_REFRESH_SESSION.log(
        FIELD_REFRESH_RECURSIVE.with(recursive),
        FIELD_REFRESH_LOCAL_ROOTS.with(lfsRoots),
        FIELD_REFRESH_ARCHIVE_ROOTS.with(arcRoots),
        FIELD_REFRESH_OTHER_ROOTS.with(otherRoots),
        FIELD_REFRESH_CANCELLED.with(cancelled),
        FIELD_WAIT_MS.with(wait),
        DurationMs.with(duration),
        FIELD_REFRESH_TRIES.with(tries));
    }
  }

  public static void logRefreshScan(int fullScans, int partialScans, int retries, long duration, long vfsTime, long ioTime) {
    if (duration >= DURATION_THRESHOLD_MS) {
      EVENT_REFRESH_SCAN.log(
        FIELD_REFRESH_FULL_SCANS.with(fullScans),
        FIELD_REFRESH_PARTIAL_SCANS.with(partialScans),
        FIELD_REFRESH_RETRIES.with(retries),
        DurationMs.with(duration),
        FIELD_REFRESH_VFS_TIME_MS.with(vfsTime),
        FIELD_REFRESH_IO_TIME_MS.with(ioTime));
    }
  }

  public static void logEventProcessing(long wait, long listenerTime, int listenerTries, long edtTime, int events) {
    if (listenerTime + edtTime >= DURATION_THRESHOLD_MS) {
      EVENT_EVENTS.log(
        FIELD_WAIT_MS.with(wait),
        FIELD_EVENT_LISTENERS_MS.with(listenerTime),
        FIELD_EVENT_TRIES.with(listenerTries),
        DurationMs.with(edtTime),
        FIELD_EVENT_NUMBER.with(events));
    }
  }

  public static void logVfsInitialization(int vfsImplementationVersion,
                                          long vfsCreationTimestamp,
                                          @NotNull VFSInitKind initKind,
                                          @NotNull List<VFSInitException.ErrorCategory> errorsHappened,
                                          int initializationAttempts,
                                          long totalInitializationDurationMs) {
    EVENT_VFS_INITIALIZATION.log(
      FIELD_INITIALIZATION_KIND.with(initKind),
      FIELD_CREATION_TIMESTAMP.with(vfsCreationTimestamp),
      FIELD_INITIALIZATION_ATTEMPTS.with(initializationAttempts),
      FIELD_IMPL_VERSION.with(vfsImplementationVersion),
      FIELD_ERRORS_HAPPENED.with(ContainerUtil.map(errorsHappened, Enum::name)),
      FIELD_TOTAL_INIT_DURATION_MS.with(totalInitializationDurationMs)
    );
  }

  public static void logVfsHealthCheck(long creationTimestampMs,
                                       long checkDurationMs,
                                       int fileRecordsChecked,
                                       int fileRecordsDeleted,
                                       int fileRecordsNameIdsNull,
                                       int fileRecordsNameIdsUnresolvable,
                                       int fileRecordsAttributeIdUnresolvable,
                                       int fileRecordsContentIdsNotNull,
                                       int fileRecordsContentIdsUnresolvable,
                                       int fileRecordsNullParents,
                                       int fileRecordsChildrenChecked,
                                       int fileRecordsInconsistentParentChildRelationships,
                                       int fileRecordsGeneralErrors,

                                       int namesChecked,
                                       int namesResolvedToNull,
                                       int namesIdsResolvedToNull,
                                       int namesInconsistentResolution,
                                       int namesGeneralErrors,

                                       int rootsChecked,
                                       int rootsWithParents,
                                       int rootsDeletedButNotRemoved,
                                       int rootsGeneralErrors,

                                       int contentRecordsChecked,
                                       int contentGeneralErrors) {
    EVENT_VFS_HEALTH_CHECK.log(
      FIELD_HEALTH_CHECK_VFS_CREATION_TIMESTAMP_MS.with(creationTimestampMs),
      FIELD_HEALTH_CHECK_DURATION_MS.with(checkDurationMs),

      FIELD_HEALTH_CHECK_FILE_RECORDS_CHECKED.with(fileRecordsChecked),
      FIELD_HEALTH_CHECK_FILE_RECORDS_DELETED.with(fileRecordsDeleted),
      FIELD_HEALTH_CHECK_FILE_RECORDS_NAME_NULL.with(fileRecordsNameIdsNull),
      FIELD_HEALTH_CHECK_FILE_RECORDS_NAME_UNRESOLVABLE.with(fileRecordsNameIdsUnresolvable),
      FIELD_HEALTH_CHECK_FILE_RECORDS_ATTRIBUTE_ID_UNRESOLVABLE.with(fileRecordsAttributeIdUnresolvable),
      FIELD_HEALTH_CHECK_FILE_RECORDS_CONTENT_NOT_NULL.with(fileRecordsContentIdsNotNull),
      FIELD_HEALTH_CHECK_FILE_RECORDS_CONTENT_UNRESOLVABLE.with(fileRecordsContentIdsUnresolvable),
      FIELD_HEALTH_CHECK_FILE_RECORDS_NULL_PARENTS.with(fileRecordsNullParents),
      FIELD_HEALTH_CHECK_FILE_RECORDS_CHILDREN_CHECKED.with(fileRecordsChildrenChecked),
      FIELD_HEALTH_CHECK_FILE_RECORDS_CHILDREN_INCONSISTENT.with(fileRecordsInconsistentParentChildRelationships),
      FIELD_HEALTH_CHECK_FILE_RECORDS_GENERAL_ERRORS.with(fileRecordsGeneralErrors),

      FIELD_HEALTH_CHECK_NAMES_CHECKED.with(namesChecked),
      FIELD_HEALTH_CHECK_NAMES_RESOLVED_TO_NULL.with(namesResolvedToNull),
      FIELD_HEALTH_CHECK_NAMES_IDS_RESOLVED_TO_NULL.with(namesIdsResolvedToNull),
      FIELD_HEALTH_CHECK_NAMES_INCONSISTENT_RESOLUTIONS.with(namesInconsistentResolution),
      FIELD_HEALTH_CHECK_NAMES_GENERAL_ERRORS.with(namesGeneralErrors),

      FIELD_HEALTH_CHECK_ROOTS_CHECKED.with(rootsChecked),
      FIELD_HEALTH_CHECK_ROOTS_WITH_PARENTS.with(rootsWithParents),
      FIELD_HEALTH_CHECK_ROOTS_DELETED_BUT_NOT_REMOVED.with(rootsDeletedButNotRemoved),
      FIELD_HEALTH_CHECK_ROOTS_ERRORS.with(rootsGeneralErrors),

      FIELD_HEALTH_CHECK_CONTENTS_CHECKED.with(contentRecordsChecked),
      FIELD_HEALTH_CHECK_CONTENTS_ERRORS.with(contentGeneralErrors)
    );
  }

  public static void logVfsInternalErrors(long vfsCreationTimestamp,
                                          long sinceStartupMs,
                                          int errorsAccumulatedSinceStartup) {
    EVENT_VFS_INTERNAL_ERRORS.log(
      FIELD_HEALTH_CHECK_VFS_CREATION_TIMESTAMP_MS.with(vfsCreationTimestamp),
      FIELD_TIME_SINCE_STARTUP.with(sinceStartupMs),
      FIELD_ACCUMULATED_VFS_ERRORS.with(errorsAccumulatedSinceStartup)
    );
  }
}
