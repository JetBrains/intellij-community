/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.externalSystem.model.task.event.OperationDescriptor;
import com.intellij.openapi.externalSystem.model.task.event.OperationResult;
import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.*;
import org.gradle.tooling.events.SkippedResult;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.TaskSuccessResult;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 12/17/2015
 */
public class GradleProgressEventConverter {

  @NotNull
  public static ExternalSystemTaskNotificationEvent convert(ExternalSystemTaskId id, ProgressEvent event) {
    return convert(id, event, "");
  }

  @NotNull
  public static ExternalSystemTaskNotificationEvent convert(@NotNull ExternalSystemTaskId id,
                                                            @NotNull ProgressEvent event,
                                                            @NotNull String operationId) {
    final InternalOperationDescriptor internalDesc =
      event.getDescriptor() instanceof DefaultOperationDescriptor ? ((DefaultOperationDescriptor)event.getDescriptor())
        .getInternalOperationDescriptor() : null;
    final String eventId;
    if (internalDesc == null) {
      eventId = operationId + event.getDescriptor().getDisplayName();
    }
    else {
      eventId = operationId + internalDesc.getId().toString();
    }
    final String parentEventId;
    if (event.getDescriptor().getParent() == null) {
      parentEventId = null;
    }
    else {
      if (internalDesc == null) {
        parentEventId = operationId + event.getDescriptor().getParent().getDisplayName();
      }
      else {
        parentEventId = operationId + internalDesc.getParentId().toString();
      }
    }

    final String description = event.getDescriptor().getName();

    if (event instanceof StartEvent) {
      final OperationDescriptor descriptor = convert(event.getDescriptor(), event.getEventTime());
      return new ExternalSystemTaskExecutionEvent(
        id, new ExternalSystemStartEventImpl<>(eventId, parentEventId, descriptor));
    }
    else if (event instanceof StatusEvent) {
      final OperationDescriptor descriptor = convert(event.getDescriptor(), event.getEventTime());
      StatusEvent statusEvent = (StatusEvent)event;
      return new ExternalSystemTaskExecutionEvent(id, new ExternalSystemStatusEventImpl<>(
        eventId, parentEventId, descriptor, statusEvent.getTotal(), statusEvent.getProgress(), statusEvent.getUnit()));
    }
    else if (event instanceof FinishEvent) {
      final OperationDescriptor descriptor = convert(event.getDescriptor(), event.getEventTime());
      return new ExternalSystemTaskExecutionEvent(
        id, new ExternalSystemFinishEventImpl<>(eventId, parentEventId, descriptor,
                                                convert(((FinishEvent)event).getResult())));
    }
    else if (event instanceof TaskProgressEvent) {
      final OperationDescriptor descriptor = convert(event.getDescriptor(), event.getEventTime());
      return new ExternalSystemTaskExecutionEvent(
        id, new BaseExternalSystemProgressEvent<>(eventId, parentEventId, descriptor));
    }
    else {
      return new ExternalSystemTaskNotificationEvent(id, description);
    }
  }

  private static OperationDescriptor convert(org.gradle.tooling.events.OperationDescriptor descriptor, long eventTime) {
    if (descriptor instanceof JvmTestOperationDescriptor) {
      final JvmTestOperationDescriptor testOperationDescriptor = (JvmTestOperationDescriptor)descriptor;
      return new TestOperationDescriptorImpl(descriptor.getName(), eventTime, testOperationDescriptor.getSuiteName(),
                                             testOperationDescriptor.getClassName(), testOperationDescriptor.getMethodName());
    }
    else if (descriptor instanceof TaskOperationDescriptor) {
      final TaskOperationDescriptor testOperationDescriptor =
        (TaskOperationDescriptor)descriptor;
      return new TaskOperationDescriptorImpl(descriptor.getName(), eventTime, testOperationDescriptor.getTaskPath());
    }
    else {
      return new OperationDescriptorImpl(descriptor.getName(), eventTime);
    }
  }

  @NotNull
  private static OperationResult convert(org.gradle.tooling.events.OperationResult operationResult) {
    if (operationResult instanceof FailureResult) {
      return new FailureResultImpl(operationResult.getStartTime(), operationResult.getEndTime());
    }
    else if (operationResult instanceof SkippedResult) {
      return new SkippedResultImpl(operationResult.getStartTime(), operationResult.getEndTime());
    }
    else {
      final boolean isUpToDate = operationResult instanceof TaskSuccessResult && ((TaskSuccessResult)operationResult).isUpToDate();
      return new SuccessResultImpl(operationResult.getStartTime(), operationResult.getEndTime(), isUpToDate);
    }
  }
}
