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

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.intellij.openapi.util.text.StringUtil.formatFileSize;

/**
 * @author Vladislav.Soroka
 * @since 4/2/2017
 */
public class GradleProgressListener implements ProgressListener, org.gradle.tooling.events.ProgressListener {
  private final ExternalSystemTaskNotificationListener myListener;
  private final ExternalSystemTaskId myTaskId;
  private final Map<String, Long> myStatusEventIds = new HashMap<>();
  private final String myBuildRootDir;
  private final int myOperationId;

  public GradleProgressListener(@NotNull ExternalSystemTaskNotificationListener listener,
                                @NotNull ExternalSystemTaskId taskId) {
    this(listener, taskId, null);
  }

  public GradleProgressListener(@NotNull ExternalSystemTaskNotificationListener listener,
                                @NotNull ExternalSystemTaskId taskId,
                                @Nullable String buildRootDir) {
    myListener = listener;
    myTaskId = taskId;
    myBuildRootDir = buildRootDir;
    myOperationId = FileUtil.pathHashCode(buildRootDir == null ? UUID.randomUUID().toString() : buildRootDir);
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    String eventDescription = event.getDescription();
    myListener.onStatusChange(new ExternalSystemTaskNotificationEvent(myTaskId, eventDescription));
    if (StringUtil.equals("Starting Gradle Daemon", eventDescription)) {
      reportGradleDaemonStartingEvent(eventDescription);
    }
  }

  @Override
  public void statusChanged(org.gradle.tooling.events.ProgressEvent event) {
    ExternalSystemTaskNotificationEvent notificationEvent = GradleProgressEventConverter.convert(myTaskId, event, myOperationId + "_");
    if (notificationEvent instanceof ExternalSystemTaskExecutionEvent) {
      ExternalSystemProgressEvent progressEvent = ((ExternalSystemTaskExecutionEvent)notificationEvent).getProgressEvent();
      if (progressEvent.getParentEventId() == null && "Run build".equals(event.getDescriptor().getName())) {
        OperationDescriptor operationDescriptor = progressEvent.getDescriptor();
        if (operationDescriptor instanceof OperationDescriptorImpl) {
          ((OperationDescriptorImpl)operationDescriptor).setHint(myBuildRootDir);
        }
      }
    }

    myListener.onStatusChange(notificationEvent);
    if (notificationEvent instanceof ExternalSystemTaskExecutionEvent) {
      ExternalSystemProgressEvent progressEvent = ((ExternalSystemTaskExecutionEvent)notificationEvent).getProgressEvent();
      if (progressEvent instanceof ExternalSystemStatusEvent) {
        ExternalSystemStatusEvent statusEvent = (ExternalSystemStatusEvent)progressEvent;
        if ("bytes".equals(statusEvent.getUnit())) {
          Long oldProgress = myStatusEventIds.get(statusEvent.getEventId());
          if (oldProgress == null) {
            String totalSizeInfo = statusEvent.getTotal() > 0 ? (" (" + formatFileSize(statusEvent.getTotal()) + ")") : "";
            myListener.onTaskOutput(myTaskId, statusEvent.getDisplayName() + totalSizeInfo + "\n", true);
            myStatusEventIds.put(statusEvent.getEventId(), 0L);
          }
          else {
            double fraction = (double)statusEvent.getProgress() / statusEvent.getTotal();
            int progressBarSize = 14;
            int progress = (int)(fraction * progressBarSize + 0.5);
            if (oldProgress != progress) {
              myStatusEventIds.put(statusEvent.getEventId(), (long)progress);
              if (statusEvent.getTotal() > 0) {
                int remaining = progressBarSize - progress;
                remaining = remaining < 0 ? 0 : remaining;
                int offset = 3 - ((int)Math.log10(fraction * 100) + 1);
                offset = offset < 0 ? 0 : offset;
                myListener.onTaskOutput(
                  myTaskId,
                  "\r[" + StringUtil.repeat(" ", offset) + (int)(fraction * 100) + "%" + ']' + " " +
                  "[ " + StringUtil.repeat("=", progress * 4 - 3) + ">" + StringUtil.repeat(" ", remaining * 4) + " ] " +
                  formatFileSize(statusEvent.getProgress()), true);
              }
              else {
                myListener.onTaskOutput(myTaskId, formatFileSize(statusEvent.getProgress()) + "\n", true);
              }
            }
          }
        }
      }
      else {
        if (progressEvent instanceof ExternalSystemFinishEvent) {
          ExternalSystemFinishEvent finishEvent = (ExternalSystemFinishEvent)progressEvent;
          if (myStatusEventIds.containsKey(finishEvent.getEventId())) {
            OperationResult operationResult = finishEvent.getOperationResult();
            String duration = StringUtil.formatDuration(operationResult.getEndTime() - operationResult.getStartTime());
            myListener.onTaskOutput(myTaskId, "\n" + finishEvent.getDisplayName() + " succeeded, took " + duration + "\n", true);
            myListener.onTaskOutput(myTaskId, "Unzipping ...\n\n", true);
            myStatusEventIds.remove(finishEvent.getEventId());
          }
        }
      }
    }
  }

  private void reportGradleDaemonStartingEvent(String eventDescription) {
    ExternalSystemTaskExecutionEvent startDaemonEvent;
    Long startTime = myStatusEventIds.get(eventDescription);
    if (startTime == null) {
      startTime = System.currentTimeMillis();
      startDaemonEvent = new ExternalSystemTaskExecutionEvent(
        myTaskId, new ExternalSystemStartEventImpl<>(eventDescription, null,
                                                     new OperationDescriptorImpl(eventDescription, startTime)));
      myStatusEventIds.put(eventDescription, startTime);
    }
    else {
      long eventTime = System.currentTimeMillis();
      startDaemonEvent = new ExternalSystemTaskExecutionEvent(
        myTaskId, new ExternalSystemFinishEventImpl<>(eventDescription, null,
                                                      new OperationDescriptorImpl(eventDescription, eventTime),
                                                      new SuccessResultImpl(startTime, eventTime, false)));
    }
    myListener.onStatusChange(startDaemonEvent);
  }
}
