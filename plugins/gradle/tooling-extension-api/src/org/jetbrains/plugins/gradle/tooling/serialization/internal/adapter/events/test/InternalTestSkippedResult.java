// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.TestSkippedResult;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class InternalTestSkippedResult implements TestSkippedResult {
  private final long startTime;
  private final long endTime;

  public InternalTestSkippedResult(long startTime, long endTime) {
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
