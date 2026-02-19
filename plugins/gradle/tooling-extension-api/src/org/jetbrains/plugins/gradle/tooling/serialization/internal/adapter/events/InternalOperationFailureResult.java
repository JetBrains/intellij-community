// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FailureResult;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;
import java.util.List;

@ApiStatus.Internal
public class InternalOperationFailureResult implements FailureResult, Serializable {
  private final long startTime;
  private final long endTime;
  private final List<? extends Failure> failures;

  public InternalOperationFailureResult(long startTime, long endTime, List<? extends Failure> failures) {
    this.startTime = startTime;
    this.endTime = endTime;
    this.failures = failures;
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
  public List<? extends Failure> getFailures() {
    return this.failures;
  }
}
