// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFinishEvent;

@ApiStatus.Internal
public final class InternalTaskFinishEvent extends InternalFinishEvent implements TaskFinishEvent {
  public InternalTaskFinishEvent(long eventTime, String displayName, TaskOperationDescriptor descriptor, TaskOperationResult result) {
    super(eventTime, displayName, descriptor, result);
  }

  @Override
  public TaskOperationDescriptor getDescriptor() {
    return (TaskOperationDescriptor)super.getDescriptor();
  }

  @Override
  public TaskOperationResult getResult() {
    return (TaskOperationResult)super.getResult();
  }
}
