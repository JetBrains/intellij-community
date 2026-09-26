// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.events.task.TaskSkippedResult;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public final class InternalTaskSkippedResult implements TaskSkippedResult, Serializable {
  private final long startTime;
  private final long endTime;
  private final String skipMessage;

  public InternalTaskSkippedResult(long startTime, long endTime, String skipMessage) {
    this.startTime = startTime;
    this.endTime = endTime;
    this.skipMessage = skipMessage;
  }

  @Override
  public long getStartTime() {
    return this.startTime;
  }

  @Override
  public long getEndTime() {
    return this.endTime;
  }

  @Override
  public String getSkipMessage() {
    return this.skipMessage;
  }
}
