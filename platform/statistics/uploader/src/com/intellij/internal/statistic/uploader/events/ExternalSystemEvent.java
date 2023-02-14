// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class ExternalSystemEvent {
  public static final String ALL_RECORDERS = "ALL";

  private final long myTimestamp;
  private final ExternalSystemEventType myEventType;

  private final @NotNull String myRecorderId;

  public ExternalSystemEvent(@NotNull ExternalSystemEventType eventType, long timestamp, @Nullable String recorderId) {
    myTimestamp = timestamp;
    myEventType = eventType;
    myRecorderId = recorderId != null ? recorderId : ALL_RECORDERS;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public ExternalSystemEventType getEventType() {
    return myEventType;
  }

  public @NotNull String getRecorderId() {
    return myRecorderId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExternalSystemEvent event = (ExternalSystemEvent)o;
    return myTimestamp == event.myTimestamp &&
           myEventType == event.myEventType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myTimestamp, myEventType);
  }
}
