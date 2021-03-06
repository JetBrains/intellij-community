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
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.internal.impldep.com.google.gson.GsonBuilder;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.test.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.Message;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.intellij.openapi.util.text.StringUtil.formatFileSize;
import static org.jetbrains.plugins.gradle.tooling.internal.ExtraModelBuilder.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX;

/**
 * @author Vladislav.Soroka
 */
public class GradleProgressListener implements ProgressListener, org.gradle.tooling.events.ProgressListener {
  private static final Logger LOG = Logger.getInstance(GradleProgressListener.class);

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

    if (event instanceof TestOutputEvent) {
      final ExternalSystemTaskNotificationEvent testNotificationEvent = convertToTestNotificationEvent((TestOutputEvent)event, eventId);
      if (testNotificationEvent != null) {
        myListener.onStatusChange(testNotificationEvent);
      }
    }

    if (event instanceof TestProgressEvent) {
      final ExternalSystemTaskNotificationEvent testNotificationEvent = convertToTestNotificationEvent((TestProgressEvent)event, eventId);
      if (testNotificationEvent != null) {
        myListener.onStatusChange(testNotificationEvent);
      }
    }


    if (event instanceof TaskProgressEvent) {
      ExternalSystemTaskNotificationEvent notificationEvent = GradleProgressEventConverter.convert(
        myTaskId, event, new GradleProgressEventConverter.EventId(eventId.id, myTaskId));
      myListener.onStatusChange(notificationEvent);
    }
  }

  private ExternalSystemTaskNotificationEvent convertToTestNotificationEvent(TestOutputEvent outputEvent,
                                                                             GradleProgressEventConverter.EventId eventId) {
    TestOutputDescriptor descriptor = outputEvent.getDescriptor();
    String prefix = outputEvent.getDescriptor().getDestination() == Destination.StdOut ? "StdOut" : "StdErr";
    String message = prefix + outputEvent.getDescriptor().getMessage();
    if (descriptor instanceof JvmTestOperationDescriptor) {
      final TestOperationDescriptor operationDescriptor = convertDescriptor(outputEvent, (JvmTestOperationDescriptor)descriptor);
      ExternalSystemMessageEvent<TestOperationDescriptor> event = new ExternalSystemMessageEventImpl<>(eventId.id.toString(),
                                                                                                       eventId.parentId.toString(),
                                                                                                       operationDescriptor,
                                                                                                       message);
      return new ExternalSystemTaskExecutionEvent(myTaskId, event);
    }

    return null;
  }

  private Failure convert(org.gradle.tooling.Failure gradleFailure) {
    return new FailureImpl(gradleFailure.getMessage(),
                           gradleFailure.getDescription(),
                           ContainerUtil.map(gradleFailure.getCauses(), this::convert));
  }

  private ExternalSystemTaskNotificationEvent convertToTestNotificationEvent(TestProgressEvent testProgressEvent,
                                                                             GradleProgressEventConverter.EventId eventId) {
    org.gradle.tooling.events.test.TestOperationDescriptor descriptor = testProgressEvent.getDescriptor();

    if (descriptor instanceof JvmTestOperationDescriptor) {
      final TestOperationDescriptor operationDescriptor = convertDescriptor(testProgressEvent, (JvmTestOperationDescriptor)descriptor);
      if (testProgressEvent instanceof TestStartEvent) {
        ExternalSystemStartEvent<TestOperationDescriptor> event = new ExternalSystemStartEventImpl<>(eventId.id.toString(),
                                                                                                     eventId.parentId.toString(),
                                                                                                     operationDescriptor);
        return new ExternalSystemTaskExecutionEvent(myTaskId, event);
      }

      if (testProgressEvent instanceof TestFinishEvent) {
        TestOperationResult gradleResult = ((TestFinishEvent)testProgressEvent).getResult();

        com.intellij.openapi.externalSystem.model.task.event.OperationResult operationResult = null;
        if (gradleResult instanceof TestSuccessResult) {
          operationResult = new SuccessResultImpl(gradleResult.getStartTime(), gradleResult.getEndTime(), true);
        } else if (gradleResult instanceof TestFailureResult) {
          TestFailureResult gradleFailure = (TestFailureResult)gradleResult;
          operationResult = new FailureResultImpl(gradleFailure.getStartTime(), gradleFailure.getEndTime(), ContainerUtil.map(gradleFailure.getFailures(), this::convert));
        } else  if (gradleResult instanceof TestSkippedResult) {
          operationResult = new SkippedResultImpl(gradleResult.getStartTime(), gradleResult.getEndTime());
        }

        if (operationResult != null) {
          ExternalSystemFinishEvent<TestOperationDescriptor> event =
            new ExternalSystemFinishEventImpl<>(eventId.id.toString(),
                                                eventId.parentId.toString(),
                                                operationDescriptor,
                                                operationResult);

          return new ExternalSystemTaskExecutionEvent(myTaskId, event);
        }
      }
    }

    return null;
  }

  @NotNull
  private static TestOperationDescriptor convertDescriptor(org.gradle.tooling.events.ProgressEvent testProgressEvent,
                                                           JvmTestOperationDescriptor descriptor) {
    String id = descriptor.getDisplayName();
    boolean parentIsTest = descriptor.getParent() instanceof org.gradle.tooling.events.test.TestOperationDescriptor;
    String parentId = parentIsTest ? descriptor.getParent().getDisplayName() : null;

    return new TestOperationDescriptorImpl(descriptor.getDisplayName(),
                                           testProgressEvent.getEventTime(),
                                           descriptor.getSuiteName(),
                                           descriptor.getClassName(),
                                           descriptor.getMethodName()
    );
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    String eventDescription = event.getDescription();
    if (maybeReportModelBuilderMessage(eventDescription)) {
      return;
    }
    ExternalSystemTaskNotificationEvent progressBuildEvent =
      GradleProgressEventConverter.legacyCreateProgressBuildEvent(myTaskId, myTaskId, eventDescription);
    maybeUpdateTaskStatus(progressBuildEvent);
    myListener.onStatusChange(new ExternalSystemTaskNotificationEvent(myTaskId, eventDescription));
    reportGradleDaemonStartingEvent(eventDescription);
  }

  private boolean maybeReportModelBuilderMessage(String eventDescription) {
    if (!eventDescription.startsWith(MODEL_BUILDER_SERVICE_MESSAGE_PREFIX)) {
      return false;
    }
    try {
      Message message = new GsonBuilder().create()
        .fromJson(StringUtil.substringAfter(eventDescription, MODEL_BUILDER_SERVICE_MESSAGE_PREFIX), Message.class);
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

  private void maybeUpdateTaskStatus(@Nullable ExternalSystemTaskNotificationEvent progressBuildEvent) {
    if (progressBuildEvent != null) {
      if (!progressBuildEvent.equals(myLastStatusChange)) {
        myListener.onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }
    }
  }

  private void sendProgressToOutputIfNeeded(org.gradle.tooling.events.ProgressEvent progressEvent) {
    @NlsSafe final String operationName = progressEvent.getDescriptor().getName();
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
          String text =
            String.format("\r%s, took %s (%s)\n", finishEvent.getDisplayName(), duration, formatFileSize(currentProgress.first));
          myListener.onTaskOutput(myTaskId, text, true);
          if (!currentProgress.first.equals(currentProgress.second)) {
            ProgressBuildEventImpl progressBuildEvent =
              new ProgressBuildEventImpl(myTaskId, myTaskId, System.currentTimeMillis(), operationName, currentProgress.first,
                                         currentProgress.first, "bytes");
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
