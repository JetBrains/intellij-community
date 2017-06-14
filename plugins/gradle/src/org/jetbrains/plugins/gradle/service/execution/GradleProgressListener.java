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
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemFinishEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemStatusEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.model.task.event.OperationResult;
import com.intellij.openapi.util.text.StringUtil;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.jetbrains.annotations.NotNull;

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
  private final Map<String, Integer> myStatusEventIds = new HashMap<>();
  private final String myOperationId;

  public GradleProgressListener(@NotNull ExternalSystemTaskNotificationListener listener, @NotNull ExternalSystemTaskId taskId) {
    this(listener, taskId, UUID.randomUUID().toString() + "_");
  }

  public GradleProgressListener(@NotNull ExternalSystemTaskNotificationListener listener,
                                @NotNull ExternalSystemTaskId taskId,
                                @NotNull String operationId) {
    myListener = listener;
    myTaskId = taskId;
    myOperationId = operationId;
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    myListener.onStatusChange(new ExternalSystemTaskNotificationEvent(myTaskId, event.getDescription()));
  }

  @Override
  public void statusChanged(org.gradle.tooling.events.ProgressEvent event) {
    ExternalSystemTaskNotificationEvent notificationEvent = GradleProgressEventConverter.convert(myTaskId, event, myOperationId);
    myListener.onStatusChange(notificationEvent);
    if (notificationEvent instanceof ExternalSystemTaskExecutionEvent) {
      if (((ExternalSystemTaskExecutionEvent)notificationEvent).getProgressEvent() instanceof ExternalSystemStatusEvent) {
        ExternalSystemStatusEvent progressEvent =
          (ExternalSystemStatusEvent)((ExternalSystemTaskExecutionEvent)notificationEvent).getProgressEvent();
        if ("bytes".equals(progressEvent.getUnit())) {
          Integer oldProgress = myStatusEventIds.get(progressEvent.getEventId());
          if (oldProgress == null) {
            String totalSizeInfo = progressEvent.getTotal() > 0 ? (" (" + formatFileSize(progressEvent.getTotal()) + ")") : "";
            myListener.onTaskOutput(myTaskId, progressEvent.getDisplayName() + totalSizeInfo + "\n", true);
            myStatusEventIds.put(progressEvent.getEventId(), 0);
          }
          else {
            double fraction = (double)progressEvent.getProgress() / progressEvent.getTotal();
            int progressBarSize = 14;
            int progress = (int)(fraction * progressBarSize + 0.5);
            if (oldProgress != progress) {
              myStatusEventIds.put(progressEvent.getEventId(), progress);
              if (progressEvent.getTotal() > 0) {
                int remaining = progressBarSize - progress;
                remaining = remaining < 0 ? 0 : remaining;
                int offset = 3 - ((int)Math.log10(fraction * 100) + 1);
                offset = offset < 0 ? 0 : offset;
                myListener.onTaskOutput(
                  myTaskId,
                  "\r[" + StringUtil.repeat(" ", offset) + (int)(fraction * 100) + "%" + ']' + " " +
                  "[ " + StringUtil.repeat("=", progress * 4 - 3) + ">" + StringUtil.repeat(" ", remaining * 4) + " ] " +
                  formatFileSize(progressEvent.getProgress()), true);
              }
              else {
                myListener.onTaskOutput(myTaskId, formatFileSize(progressEvent.getProgress()) + "\n", true);
              }
            }
          }
        }
      }
      else {
        if (((ExternalSystemTaskExecutionEvent)notificationEvent).getProgressEvent() instanceof ExternalSystemFinishEvent) {
          ExternalSystemFinishEvent progressEvent =
            (ExternalSystemFinishEvent)((ExternalSystemTaskExecutionEvent)notificationEvent).getProgressEvent();
          if (myStatusEventIds.containsKey(progressEvent.getEventId())) {
            OperationResult operationResult = progressEvent.getOperationResult();
            String duration = StringUtil.formatDuration(operationResult.getEndTime() - operationResult.getStartTime());
            myListener.onTaskOutput(myTaskId, "\n" + progressEvent.getDisplayName() + " succeeded, took " + duration + "\n", true);
            myListener.onTaskOutput(myTaskId, "Unzipping ...\n\n", true);
            myStatusEventIds.remove(progressEvent.getEventId());
          }
        }
      }
    }
  }
}
