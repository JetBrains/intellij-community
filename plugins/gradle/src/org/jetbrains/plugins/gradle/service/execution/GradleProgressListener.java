/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.build.events.impl.ProgressBuildEventImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.intellij.openapi.util.text.StringUtil.formatFileSize;

/**
 * @author Vladislav.Soroka
 */
public class GradleProgressListener implements ProgressListener, org.gradle.tooling.events.ProgressListener {
  private final ExternalSystemTaskNotificationListener myListener;
  private final ExternalSystemTaskId myTaskId;
  private final Map<Object, Long> myStatusEventIds = new HashMap<>();
  private final Map<Object, Couple<Long>> myDownloadStatusEventIds = new HashMap<>();
  private final String myOperationId;
  private static final String STARTING_GRADLE_DAEMON_EVENT = "Starting Gradle Daemon";
  private ExternalSystemTaskNotificationEvent myLastStatusChange = null;

  public GradleProgressListener(@NotNull ExternalSystemTaskNotificationListener listener,
                                @NotNull ExternalSystemTaskId taskId) {
    this(listener, taskId, null);
  }

  public GradleProgressListener(@NotNull ExternalSystemTaskNotificationListener listener,
                                @NotNull ExternalSystemTaskId taskId,
                                @Nullable String buildRootDir) {
    myListener = listener;
    myTaskId = taskId;
    myOperationId = (taskId.hashCode() + FileUtil.pathHashCode(buildRootDir == null ? UUID.randomUUID().toString() : buildRootDir)) + "_";
  }

  @Override
  public void statusChanged(org.gradle.tooling.events.ProgressEvent event) {
    GradleProgressEventConverter.EventId eventId = GradleProgressEventConverter.getEventId(event, myOperationId);
    ExternalSystemTaskNotificationEvent progressBuildEvent =
      GradleProgressEventConverter.createProgressBuildEvent(myTaskId, myTaskId, event);
    sendProgressToOutputIfNeeded(event);
    if (progressBuildEvent != null && event instanceof StatusEvent) {
      // update IDE progress determinate indicator
      myListener.onStatusChange(progressBuildEvent);
    }

    maybeUpdateTaskStatus(progressBuildEvent);
    if (event instanceof TaskProgressEvent) {
      ExternalSystemTaskNotificationEvent notificationEvent = GradleProgressEventConverter.convert(
        myTaskId, event, new GradleProgressEventConverter.EventId(eventId.id, myTaskId));
      myListener.onStatusChange(notificationEvent);
    }
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    String eventDescription = event.getDescription();
    ExternalSystemTaskNotificationEvent progressBuildEvent =
      GradleProgressEventConverter.legacyCreateProgressBuildEvent(myTaskId, myTaskId, eventDescription);
    maybeUpdateTaskStatus(progressBuildEvent);
    myListener.onStatusChange(new ExternalSystemTaskNotificationEvent(myTaskId, eventDescription));
    reportGradleDaemonStartingEvent(eventDescription);
  }

  private void maybeUpdateTaskStatus(@Nullable ExternalSystemTaskNotificationEvent progressBuildEvent) {
    if (progressBuildEvent != null) {
      if (!progressBuildEvent.equals(myLastStatusChange)) {
        myListener.onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }
    }
  }

  private void sendProgressToOutputIfNeeded(org.gradle.tooling.events.ProgressEvent progressEvent) {
    final String operationName = progressEvent.getDescriptor().getName();
    if (progressEvent instanceof StatusEvent) {
      StatusEvent statusEvent = ((StatusEvent)progressEvent);
      if ("bytes".equals(statusEvent.getUnit())) {
        Couple<Long> oldProgress = myDownloadStatusEventIds.get(operationName);
        if (oldProgress == null) {
          String totalSizeInfo = statusEvent.getTotal() > 0 ? (" (" + formatFileSize(statusEvent.getTotal()) + ")") : "";
          myListener.onTaskOutput(myTaskId, operationName + totalSizeInfo, true);
          myDownloadStatusEventIds.put(operationName, Couple.of(statusEvent.getTotal(), statusEvent.getProgress()));
        }
        else {
          if (!oldProgress.second.equals(statusEvent.getProgress())) {
            myDownloadStatusEventIds.put(operationName, Couple.of(statusEvent.getTotal(), statusEvent.getProgress()));
            if (statusEvent.getTotal() > 0) {
              String sizeInfo = " (" + formatFileSize(statusEvent.getProgress()) + "/ " + formatFileSize(statusEvent.getTotal()) + ")";
              myListener.onTaskOutput(myTaskId, "\r" + operationName + sizeInfo, true);
            }
            else {
              myListener.onTaskOutput(myTaskId, formatFileSize(statusEvent.getProgress()) + "\n", true);
            }
          }
        }
      }
    }
    else {
      if (progressEvent instanceof FinishEvent) {
        FinishEvent finishEvent = (FinishEvent)progressEvent;
        Couple<Long> currentProgress = myDownloadStatusEventIds.remove(operationName);
        if (currentProgress != null) {
          OperationResult operationResult = finishEvent.getResult();
          String duration = StringUtil.formatDuration(operationResult.getEndTime() - operationResult.getStartTime());
          String text = String.format("\r%s, took %s (%s)\n", finishEvent.getDisplayName(), duration, formatFileSize(currentProgress.first));
          myListener.onTaskOutput(myTaskId, text, true);
          if (!currentProgress.first.equals(currentProgress.second)) {
            ProgressBuildEventImpl progressBuildEvent =
              new ProgressBuildEventImpl(myTaskId, myTaskId, System.currentTimeMillis(), operationName, currentProgress.first, currentProgress.first, "bytes");
            myListener.onStatusChange(new ExternalSystemBuildEvent(myTaskId, progressBuildEvent));
          }
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
        String duration = StringUtil.formatDuration(eventTime - startTime);
        myListener.onTaskOutput(myTaskId, "\rGradle Daemon started in " + duration + "\n", true);
      }
    }
  }
}
