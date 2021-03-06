// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationFailureResult;

import java.util.List;

@ApiStatus.Internal
public final class InternalTaskFailureResult extends InternalOperationFailureResult implements TaskFailureResult {
  private final InternalTaskExecutionDetails taskExecutionDetails;

  public InternalTaskFailureResult(long startTime,
                                   long endTime,
                                   List<? extends Failure> failures,
                                   InternalTaskExecutionDetails taskExecutionDetails) {
    super(startTime, endTime, failures);
    this.taskExecutionDetails = taskExecutionDetails;
  }

  @Override
  public boolean isIncremental() {
    return this.taskExecutionDetails.isIncremental();
  }

  @Override
  public List<String> getExecutionReasons() {
    return this.taskExecutionDetails.getExecutionReasons();
  }
}
