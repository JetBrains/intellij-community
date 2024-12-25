// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config.bean;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class EventLogSendConfiguration {
  private final List<EventLogBucketRange> myBuckets;

  public EventLogSendConfiguration(@NotNull List<EventLogBucketRange> buckets) {
    myBuckets = buckets;
  }

  public void addBucketRange(EventLogBucketRange range) {
    myBuckets.add(range);
  }

  public @NotNull List<EventLogBucketRange> getBuckets() {
    return myBuckets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EventLogSendConfiguration that = (EventLogSendConfiguration)o;
    return Objects.equals(myBuckets, that.myBuckets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myBuckets);
  }
}
