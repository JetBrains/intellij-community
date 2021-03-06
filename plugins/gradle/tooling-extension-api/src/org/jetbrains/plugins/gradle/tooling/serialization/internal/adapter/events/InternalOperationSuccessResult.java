// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.SuccessResult;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public class InternalOperationSuccessResult implements SuccessResult, Serializable {
  private final long startTime;
  private final long endTime;

  public InternalOperationSuccessResult(long startTime, long endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  @Override
  public long getStartTime() {
    return this.startTime;
  }

  @Override
  public long getEndTime() {
    return this.endTime;
  }
}
