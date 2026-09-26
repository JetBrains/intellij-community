// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.events;

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType;
import com.intellij.internal.statistic.eventLog.FileDeletionCause;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Recorded by the external upload process for each log file deleted as a result of a send attempt.
 * Replayed into the FUS pipeline on the next IDE startup.
 */
public final class ExternalUploadFileDeletedEvent extends ExternalSystemEvent {
  private final @NotNull FileDeletionCause myCause;
  private final long mySizeBytes;
  private final long myAgeMs;
  private final long myQueuedMs;
  private final @NotNull EventLogBuildType myBuildType;

  public ExternalUploadFileDeletedEvent(long timestamp, @NotNull FileDeletionCause cause,
                                        long sizeBytes, long ageMs, long queuedMs,
                                        @NotNull EventLogBuildType buildType,
                                        @NotNull String recorderId) {
    super(ExternalSystemEventType.FILE_DELETED, timestamp, recorderId);
    myCause = cause;
    mySizeBytes = sizeBytes;
    myAgeMs = ageMs;
    myQueuedMs = queuedMs;
    myBuildType = buildType;
  }

  public @NotNull FileDeletionCause getCause() {
    return myCause;
  }

  public long getSizeBytes() {
    return mySizeBytes;
  }

  public long getAgeMs() {
    return myAgeMs;
  }

  public long getQueuedMs() {
    return myQueuedMs;
  }

  public @NotNull EventLogBuildType getBuildType() {
    return myBuildType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExternalUploadFileDeletedEvent event = (ExternalUploadFileDeletedEvent)o;
    return myCause == event.myCause &&
           mySizeBytes == event.mySizeBytes &&
           myAgeMs == event.myAgeMs &&
           myQueuedMs == event.myQueuedMs &&
           myBuildType == event.myBuildType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myCause, mySizeBytes, myAgeMs, myQueuedMs, myBuildType);
  }
}
