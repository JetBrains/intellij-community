// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.build.events.EventResult;
import com.intellij.build.events.impl.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.util.NlsSafe;
import org.gradle.tooling.events.*;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.TaskSuccessResult;
import org.gradle.tooling.events.test.TestProgressEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Vladislav.Soroka
 */
public final class GradleProgressEventConverter {

  static EventId getEventId(@NotNull ProgressEvent event, @NotNull String operationId) {
    OperationDescriptor descriptor = event.getDescriptor();
    String eventId = operationId + descriptor.getDisplayName();
    String parentEventId = descriptor.getParent() == null ? null :
                           operationId + descriptor.getParent().getDisplayName();
    return new EventId(eventId, parentEventId);
  }

  @NotNull
  public static ExternalSystemTaskNotificationEvent convert(@NotNull ExternalSystemTaskId id,
                                                            @NotNull ProgressEvent event,
                                                            @NotNull String operationId) {
    return convert(id, event, getEventId(event, operationId));
  }

  @NotNull
  public static ExternalSystemTaskNotificationEvent convert(@NotNull ExternalSystemTaskId id,
                                                            @NotNull ProgressEvent event,
                                                            @NotNull EventId eventId) {
    @NlsSafe final String description = event.getDescriptor().getName();

    if (event instanceof StartEvent) {
      return new ExternalSystemBuildEvent(
        id, new StartEventImpl(eventId.id, eventId.parentId, event.getEventTime(), description));
    }
    else if (event instanceof StatusEvent) {
      StatusEvent statusEvent = (StatusEvent)event;
      return new ExternalSystemBuildEvent(id, new ProgressBuildEventImpl(
        eventId.id, eventId.parentId, event.getEventTime(), description, statusEvent.getTotal(), statusEvent.getProgress(),
        statusEvent.getUnit()));
    }
    else if (event instanceof FinishEvent) {
      return new ExternalSystemBuildEvent(
        id,
        new FinishEventImpl(eventId.id, eventId.parentId, event.getEventTime(), description, convert(((FinishEvent)event).getResult())));
    }
    else if (event instanceof TaskProgressEvent) {
      return new ExternalSystemBuildEvent(
        id, new ProgressBuildEventImpl(eventId.id, eventId.parentId, event.getEventTime(), description, -1, -1, ""));
    }
    else {
      return new ExternalSystemTaskNotificationEvent(id, description);
    }
  }

  @NotNull
  public static ExternalSystemTaskNotificationEvent convert(ExternalSystemTaskId id, ProgressEvent event) {
    return convert(id, event, "");
  }

  @NotNull
  private static EventResult convert(OperationResult operationResult) {
    if (operationResult instanceof FailureResult) {
      return new FailureResultImpl(null, null);
    }
    else if (operationResult instanceof SkippedResult) {
      return new SkippedResultImpl();
    }
    else {
      final boolean isUpToDate = operationResult instanceof TaskSuccessResult && ((TaskSuccessResult)operationResult).isUpToDate();
      return new SuccessResultImpl(isUpToDate);
    }
  }

  @Nullable
  static ExternalSystemTaskNotificationEvent createProgressBuildEvent(@NotNull ExternalSystemTaskId taskId,
                                                                      @NotNull Object id,
                                                                      @NotNull ProgressEvent event) {
    long total = -1;
    long progress = -1;
    String unit = "";
    @NlsSafe String operationName = event.getDescriptor().getName();
    if (operationName.startsWith("Download ")) {
      String path = operationName.substring("Download ".length());
      operationName = GradleBundle.message("progress.title.download", getFileName(path));
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

  @Nullable
  static ExternalSystemTaskNotificationEvent legacyCreateProgressBuildEvent(@NotNull ExternalSystemTaskId taskId,
                                                                            @NotNull Object id,
                                                                            @NotNull String event) {
    long total = -1;
    long progress = -1;
    String unit = "";
    @NlsSafe String operationName = event;
    if (operationName.startsWith("Download ")) {
      String path = operationName.substring("Download ".length());
      operationName = GradleBundle.message("progress.title.download", getFileName(path));
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

  @NotNull
  private static String getFileName(String path) {
    int index = path.lastIndexOf('/');
    if (index > 0 && index < path.length()) {
      String fileName = path.substring(index + 1);
      if (!fileName.isEmpty()) return fileName;
    }
    return path;
  }

  static class EventId {
    Object id;
    Object parentId;

    EventId(Object id, Object parentId) {
      this.id = id;
      this.parentId = parentId;
    }
  }
}
