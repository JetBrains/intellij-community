// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.build.events.EventResult;
import com.intellij.build.events.impl.FinishEventImpl;
import com.intellij.build.events.impl.ProgressBuildEventImpl;
import com.intellij.build.events.impl.StartEventImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.OperationResult;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.SkippedResult;
import org.gradle.tooling.events.SuccessResult;
import org.gradle.tooling.events.*;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.TaskStartEvent;
import org.gradle.tooling.events.task.TaskSuccessResult;
import org.gradle.tooling.events.test.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.List;
import java.util.StringJoiner;

/**
 * @author Vladislav.Soroka
 */
public final class GradleProgressEventConverter {

  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.externalSystem.event-processing");

  private static @NotNull String createEventId(@NotNull OperationDescriptor descriptor, @NotNull String operationId) {
    var joiner = new StringJoiner(" > ");
    joiner.add("[" + operationId + "]");
    var currentDescriptor = descriptor;
    while (currentDescriptor != null) {
      joiner.add("[" + currentDescriptor.getDisplayName() + "]");
      currentDescriptor = currentDescriptor.getParent();
    }
    return joiner.toString();
  }

  public static @Nullable ExternalSystemTaskNotificationEvent createTaskNotificationEvent(
    @NotNull ExternalSystemTaskId taskId,
    @NotNull String operationId,
    @NotNull ProgressEvent event
  ) {
    if (event instanceof TaskProgressEvent taskProgressEvent) {
      return convertTaskProgressEvent(taskProgressEvent, taskId, operationId);
    }
    return convertTestProgressEvent(event, taskId, operationId);
  }

  private static @NotNull ExternalSystemTaskNotificationEvent convertTaskProgressEvent(
    @NotNull TaskProgressEvent event,
    @NotNull ExternalSystemTaskId taskId,
    @NotNull String operationId
  ) {
    var eventId = createEventId(event.getDescriptor(), operationId);
    var eventTime = event.getEventTime();
    var message = event.getDescriptor().getName();

    if (event instanceof TaskStartEvent) {
      return new ExternalSystemBuildEvent(taskId, new StartEventImpl(eventId, taskId, eventTime, message));
    }
    else if (event instanceof TaskFinishEvent finishEvent) {
      var result = finishEvent.getResult();
      var eventResult = convertTaskProgressEventResult(result);
      if (eventResult != null) {
        return new ExternalSystemBuildEvent(taskId, new FinishEventImpl(eventId, taskId, eventTime, message, eventResult));
      }
    }
    else if (event instanceof StatusEvent statusEvent) {
      var total = statusEvent.getTotal();
      var progress = statusEvent.getProgress();
      var unit = statusEvent.getUnit();
      return new ExternalSystemBuildEvent(taskId, new ProgressBuildEventImpl(eventId, taskId, eventTime, message, total, progress, unit));
    }
    LOG.warn("Undefined Gradle event " + event.getClass().getSimpleName() + " " + event);
    var description = event.getDescriptor().getName();
    return new ExternalSystemTaskNotificationEvent(taskId, description);
  }

  private static @Nullable EventResult convertTaskProgressEventResult(@NotNull org.gradle.tooling.events.OperationResult result) {
    if (result instanceof SuccessResult) {
      var isUpToDate = result instanceof TaskSuccessResult && ((TaskSuccessResult)result).isUpToDate();
      return new com.intellij.build.events.impl.SuccessResultImpl(isUpToDate);
    }
    if (result instanceof FailureResult) {
      return new com.intellij.build.events.impl.FailureResultImpl(null, null);
    }
    if (result instanceof SkippedResult) {
      return new com.intellij.build.events.impl.SkippedResultImpl();
    }
    LOG.warn("Undefined operation result " + result.getClass().getSimpleName() + " " + result);
    return null;
  }

  private static @Nullable ExternalSystemTaskNotificationEvent convertTestProgressEvent(
    @NotNull ProgressEvent event,
    @NotNull ExternalSystemTaskId taskId,
    @NotNull String operationId
  ) {
    var eventId = createEventId(event.getDescriptor(), operationId);
    var parentEventId = ObjectUtils.doIfNotNull(event.getDescriptor().getParent(), it -> createEventId(it, operationId));
    var descriptor = convertDescriptor(event);

    if (event instanceof TestStartEvent) {
      return new ExternalSystemTaskExecutionEvent(taskId, new ExternalSystemStartEventImpl<>(eventId, parentEventId, descriptor));
    }
    if (event instanceof TestFinishEvent finishEvent) {
      var result = finishEvent.getResult();
      var operationResult = convertTestProgressEventResult(result);
      if (operationResult != null) {
        var esEvent = new ExternalSystemFinishEventImpl<>(eventId, parentEventId, descriptor, operationResult);
        return new ExternalSystemTaskExecutionEvent(taskId, esEvent);
      }
    }
    if (event instanceof TestOutputEvent outputEvent) {
      var outputDescriptor = outputEvent.getDescriptor();
      var destination = outputDescriptor.getDestination();
      var isStdOut = destination == Destination.StdOut;
      var message = outputDescriptor.getMessage();
      var description = (isStdOut ? "StdOut" : "StdErr") + message;
      var esEvent = new ExternalSystemMessageEventImpl<>(eventId, parentEventId, descriptor, isStdOut, message, description);
      return new ExternalSystemTaskExecutionEvent(taskId, esEvent);
    }
    return null;
  }

  private static @Nullable OperationResult convertTestProgressEventResult(@NotNull org.gradle.tooling.events.OperationResult result) {
    var startTime = result.getStartTime();
    var endTime = result.getEndTime();
    if (result instanceof SuccessResult) {
      var isUpToDate = result instanceof TaskSuccessResult && ((TaskSuccessResult)result).isUpToDate();
      return new SuccessResultImpl(startTime, endTime, isUpToDate);
    }
    if (result instanceof FailureResult) {
      var failures = convertFailureResult((FailureResult)result);
      return new FailureResultImpl(startTime, endTime, failures);
    }
    if (result instanceof SkippedResult) {
      return new SkippedResultImpl(startTime, endTime);
    }
    LOG.warn("Undefined operation result " + result.getClass().getSimpleName() + " " + result);
    return null;
  }

  private static @NotNull List<Failure> convertFailureResult(@NotNull FailureResult failure) {
    return ContainerUtil.map(failure.getFailures(), it -> convertFailure(it));
  }

  private static @NotNull Failure convertFailure(@NotNull org.gradle.tooling.Failure failure) {
    return new FailureImpl(
      failure.getMessage(),
      failure.getDescription(),
      ContainerUtil.map(failure.getCauses(), it -> convertFailure(it))
    );
  }

  private static @NotNull TestOperationDescriptor convertDescriptor(@NotNull ProgressEvent event) {
    var descriptor = event.getDescriptor();
    var eventTime = event.getEventTime();
    var displayName = descriptor.getDisplayName();
    if (descriptor instanceof JvmTestOperationDescriptor jvmDescriptor) {
      var suiteName = jvmDescriptor.getSuiteName();
      var className = jvmDescriptor.getClassName();
      var methodName = jvmDescriptor.getMethodName();
      return new TestOperationDescriptorImpl(displayName, eventTime, suiteName, className, methodName);
    }
    return new TestOperationDescriptorImpl(displayName, eventTime, null, null, null);
  }

  public static @Nullable ExternalSystemTaskNotificationEvent createProgressBuildEvent(
    @NotNull ExternalSystemTaskId taskId,
    @NotNull Object id,
    @NotNull ProgressEvent event
  ) {
    long total = -1;
    long progress = -1;
    String unit = "";
    @NlsSafe String operationName = event.getDescriptor().getName();
    if (operationName.startsWith("Download ")) {
      String path = operationName.substring("Download ".length());
      operationName = GradleBundle.message("progress.title.download", PathUtil.getFileName(path));
    }
    else if (event instanceof TaskProgressEvent) {
      operationName = GradleBundle.message("progress.title.run.tasks");
    }
    else if (event instanceof TestProgressEvent) {
      operationName = GradleBundle.message("progress.title.run.tests");
    }
    else if (event.getDisplayName().startsWith("Configure project ") || event.getDisplayName().startsWith("Cross-configure project ")) {
      operationName = GradleBundle.message("progress.title.configure.projects");
    }
    else {
      return null;
    }
    if (event instanceof StatusEvent) {
      total = ((StatusEvent)event).getTotal();
      progress = ((StatusEvent)event).getProgress();
      unit = ((StatusEvent)event).getUnit();
    }
    return new ExternalSystemBuildEvent(
      taskId, new ProgressBuildEventImpl(id, null, event.getEventTime(), operationName + "...", total, progress, unit));
  }

  public static @Nullable ExternalSystemTaskNotificationEvent legacyCreateProgressBuildEvent(
    @NotNull ExternalSystemTaskId taskId,
    @NotNull Object id,
    @NotNull String event
  ) {
    long total = -1;
    long progress = -1;
    String unit = "";
    @NlsSafe String operationName = event;
    if (operationName.startsWith("Download ")) {
      String path = operationName.substring("Download ".length());
      operationName = GradleBundle.message("progress.title.download", PathUtil.getFileName(path));
    }
    else if (operationName.startsWith("Task: ")) {
      operationName = GradleBundle.message("progress.title.run.tasks");
    }
    else if (operationName.equals("Build")) {
      operationName = GradleBundle.message("progress.title.build");
    }
    else if (operationName.startsWith("Build model ") || operationName.startsWith("Build parameterized model")) {
      operationName = GradleBundle.message("progress.title.build.model");
    }
    else if (operationName.startsWith("Configure project ") || operationName.startsWith("Cross-configure project ")) {
      operationName = GradleBundle.message("progress.title.configure.projects");
    }
    else {
      return null;
    }
    return new ExternalSystemBuildEvent(
      taskId, new ProgressBuildEventImpl(id, null, 0, operationName + "...", total, progress, unit));
  }

  public static @NotNull ExternalSystemTaskNotificationEvent legacyCreateTaskNotificationEvent(
    @NotNull ExternalSystemTaskId taskId,
    @NotNull String event
  ) {
    return new ExternalSystemTaskNotificationEvent(taskId, event);
  }
}
