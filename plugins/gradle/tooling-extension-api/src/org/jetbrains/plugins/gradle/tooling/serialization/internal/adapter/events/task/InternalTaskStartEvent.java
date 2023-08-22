// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskStartEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalStartEvent;

@ApiStatus.Internal
public final class InternalTaskStartEvent extends InternalStartEvent implements TaskStartEvent {
  public InternalTaskStartEvent(long eventTime, String displayName, TaskOperationDescriptor descriptor) {
    super(eventTime, displayName, descriptor);
  }

  @Override
  public TaskOperationDescriptor getDescriptor() {
    return (TaskOperationDescriptor)super.getDescriptor();
  }
}
