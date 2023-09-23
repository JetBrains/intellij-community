// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.events.impl.ProgressBuildEventImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import org.gradle.internal.impldep.com.google.gson.GsonBuilder;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.StatusEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.intellij.openapi.util.text.StringUtil.formatDuration;
import static com.intellij.openapi.util.text.StringUtil.formatFileSize;

/**
 * @author Vladislav.Soroka
 */
public class GradleProgressListener implements ProgressListener, org.gradle.tooling.ProgressListener {
  private static final Logger LOG = Logger.getInstance(GradleProgressListener.class);

  private final ExternalSystemTaskNotificationListener myListener;
  private final ExternalSystemTaskId myTaskId;
  private final Map<Object, Long> myStatusEventIds = new HashMap<>();
  private final Map<Object, StatusEvent> myDownloadStatusEventIds = new HashMap<>();
  private final String myOperationId;
  private static final String STARTING_GRADLE_DAEMON_EVENT = "Starting Gradle Daemon";
  private ExternalSystemTaskNotificationEvent myLastStatusChange = null;

  public GradleProgressListener(
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull ExternalSystemTaskId taskId
  ) {
    this(listener, taskId, null);
  }

  public GradleProgressListener(
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull ExternalSystemTaskId taskId,
    @Nullable String buildRootDir
  ) {
    myListener = listener;
    myTaskId = taskId;
    myOperationId = taskId.hashCode() + ":" + FileUtil.pathHashCode(buildRootDir == null ? UUID.randomUUID().toString() : buildRootDir);
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    sendProgressToOutputIfNeeded(event);

    var progressBuildEvent = GradleProgressEventConverter.convertProgressBuildEvent(myTaskId, myTaskId, event);
    if (progressBuildEvent != null) {
      if (event instanceof StatusEvent) {
        // update IDE progress determinate indicator
        myListener.onStatusChange(progressBuildEvent);
      }
      else if (!progressBuildEvent.equals(myLastStatusChange)) {
        myListener.onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }
    }

    var taskNotificationEvent = GradleProgressEventConverter.createTaskNotificationEvent(myTaskId, myOperationId, event);
    if (taskNotificationEvent != null) {
      myListener.onStatusChange(taskNotificationEvent);
    }
  }

  @Override
  public void statusChanged(org.gradle.tooling.ProgressEvent event) {
    var eventDescription = event.getDescription();
    if (!maybeReportModelBuilderMessage(eventDescription)) {
      var progressBuildEvent = GradleProgressEventConverter.legacyConvertProgressBuildEvent(myTaskId, myTaskId, eventDescription);
      if (progressBuildEvent != null && !progressBuildEvent.equals(myLastStatusChange)) {
        myListener.onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }

      var taskNotificationEvent = GradleProgressEventConverter.legacyConvertTaskNotificationEvent(myTaskId, eventDescription);
      myListener.onStatusChange(taskNotificationEvent);

      reportGradleDaemonStartingEvent(eventDescription);
    }
  }

  private boolean maybeReportModelBuilderMessage(String eventDescription) {
    if (!eventDescription.startsWith(MessageReporter.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX)) {
      return false;
    }
    try {
      Message message = new GsonBuilder().create()
        .fromJson(StringUtil.substringAfter(eventDescription, MessageReporter.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX), Message.class);
      MessageEvent.Kind kind = MessageEvent.Kind.valueOf(message.getKind().name());
      Message.FilePosition messageFilePosition = message.getFilePosition();
      FilePosition filePosition = messageFilePosition == null ? null :
                                  new FilePosition(new File(messageFilePosition.getFilePath()), messageFilePosition.getLine(),
                                                   messageFilePosition.getColumn());
      MessageEvent messageEvent = new MessageEventImpl(myTaskId, kind, message.getGroup(), message.getTitle(), message.getText()) {
        @Override
        public @Nullable Navigatable getNavigatable(@NotNull Project project) {
          if (filePosition == null) return null;
          return new FileNavigatable(project, filePosition);
        }
      };

      myListener.onStatusChange(new ExternalSystemBuildEvent(myTaskId, messageEvent));
      return true;
    }
    catch (Exception e) {
      LOG.warn("Failed to report model builder message using event '" + eventDescription + "'", e);
    }
    return false;
  }

  private void sendProgressToOutputIfNeeded(ProgressEvent progressEvent) {
    @NlsSafe final String operationName = progressEvent.getDescriptor().getName();
    if (progressEvent instanceof StatusEvent statusEvent) {
      if ("bytes".equals(statusEvent.getUnit())) {
        StatusEvent oldStatusEvent = myDownloadStatusEventIds.get(operationName);
        myDownloadStatusEventIds.put(operationName, statusEvent);
        if (oldStatusEvent == null || oldStatusEvent.getProgress() != statusEvent.getProgress()) {
          long progress = statusEvent.getProgress() > 0 ? statusEvent.getProgress() : 0;
          long total = statusEvent.getTotal() > 0 ? statusEvent.getTotal() : 0;
          String text = String.format("%s (%s / %s)", operationName, formatFileSize(progress), formatFileSize(total));
          if (oldStatusEvent == null) {
            myListener.onTaskOutput(myTaskId, text, true);
          }
          else {
            myListener.onTaskOutput(myTaskId, "\r" + text, true);
          }
        }
      }
    }
    else if (progressEvent instanceof FinishEvent finishEvent) {
      StatusEvent statusEvent = myDownloadStatusEventIds.remove(operationName);
      if (statusEvent != null) {
        var operationResult = finishEvent.getResult();
        long duration = operationResult.getEndTime() - operationResult.getStartTime();
        long progress = statusEvent.getProgress() > 0 ? statusEvent.getProgress() : 0;
        long total = statusEvent.getTotal() > 0 ? statusEvent.getTotal() : 0;
        String text = String.format("%s, took %s (%s)", operationName, formatDuration(duration), formatFileSize(total));
        myListener.onTaskOutput(myTaskId, "\r" + text + "\n", true);
        if (total != progress) {
          ProgressBuildEventImpl progressBuildEvent =
            new ProgressBuildEventImpl(myTaskId, myTaskId, System.currentTimeMillis(), operationName, total, progress, "bytes");
          myListener.onStatusChange(new ExternalSystemBuildEvent(myTaskId, progressBuildEvent));
        }
      }
    }
  }

  private void reportGradleDaemonStartingEvent(String eventDescription) {
    if (StringUtil.equals(STARTING_GRADLE_DAEMON_EVENT, eventDescription)) {
      long eventTime = System.currentTimeMillis();
      Long startTime = myStatusEventIds.remove(eventDescription);
      if (startTime == null) {
        myListener.onTaskOutput(myTaskId, STARTING_GRADLE_DAEMON_EVENT + "...\n", true);
        myStatusEventIds.put(eventDescription, eventTime);
      }
      else {
        String duration = formatDuration(eventTime - startTime);
        myListener.onTaskOutput(myTaskId, "\rGradle Daemon started in " + duration + "\n", true);
      }
    }
  }
}
