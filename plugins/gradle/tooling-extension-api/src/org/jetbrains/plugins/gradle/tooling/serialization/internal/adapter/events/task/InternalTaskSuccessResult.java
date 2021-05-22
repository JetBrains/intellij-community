// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.events.task.TaskSuccessResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationSuccessResult;

import java.util.List;

@ApiStatus.Internal
public final class InternalTaskSuccessResult extends InternalOperationSuccessResult implements TaskSuccessResult {
  private final boolean upToDate;
  private final boolean fromCache;
  private final InternalTaskExecutionDetails taskExecutionDetails;

  public InternalTaskSuccessResult(long startTime,
                                   long endTime,
                                   boolean upToDate,
                                   boolean fromCache,
                                   InternalTaskExecutionDetails taskExecutionDetails) {
    super(startTime, endTime);
    this.upToDate = upToDate;
    this.fromCache = fromCache;
    this.taskExecutionDetails = taskExecutionDetails;
  }

  @Override
  public boolean isUpToDate() {
    return this.upToDate;
  }

  @Override
  public boolean isFromCache() {
    return this.fromCache;
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
