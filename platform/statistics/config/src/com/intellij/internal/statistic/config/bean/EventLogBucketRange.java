// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.config.bean;

import java.util.Objects;

public class EventLogBucketRange {
  private final int from;
  private final int to;

  public EventLogBucketRange(int from, int to) {
    this.from = from;
    this.to = to;
  }

  public boolean contains(int bucket) {
    return from <= bucket && bucket < to;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EventLogBucketRange range = (EventLogBucketRange)o;
    return from == range.from &&
           to == range.to;
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to);
  }
}
