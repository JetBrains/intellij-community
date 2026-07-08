// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.google.gson.GsonBuilder;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileDownloadEventImpl;
import com.intellij.build.events.impl.FileDownloadedEventImpl;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.formatDuration;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class GradleProgressListener implements ProgressListener, org.gradle.tooling.ProgressListener {
  private static final Logger LOG = Logger.getInstance(GradleProgressListener.class);
  public static final String SEND_PROGRESS_EVENTS_TO_OUTPUT_KEY = "gradle.output.sync.progress.events";
  private final GradleDownloadProgressMapper myDownloadProgressMapper;
  private final GradleExecutionReporter myReporter;
  private final ExternalSystemTaskNotificationListener myListener;
  private final GradleExecutionProgressMapper myProgressMapper;
  private final ExternalSystemTaskId myTaskId;
  private final Map<Object, Long> myStatusEventIds = new HashMap<>();
  private final String myOperationId;
  private static final String EXECUTING_BUILD = "Build";
  private static final String STARTING_GRADLE_DAEMON_EVENT = "Starting Gradle Daemon";
  private ExternalSystemTaskNotificationEvent myLastStatusChange = null;
  private final boolean sendProgressEventsToOutput;

  public GradleProgressListener(
    @NotNull ExternalSystemTaskId taskId,
    @NotNull GradleExecutionReporter reporter,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull String buildRootDir
  ) {
    myTaskId = taskId;
    myReporter = reporter;
    myListener = listener;
    myOperationId = taskId.hashCode() + ":" + FileUtil.pathHashCode(buildRootDir);
    myProgressMapper = new GradleExecutionProgressMapper();
    myDownloadProgressMapper = new GradleDownloadProgressMapper();
    sendProgressEventsToOutput = Registry.is(SEND_PROGRESS_EVENTS_TO_OUTPUT_KEY, true);
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    if (myDownloadProgressMapper.canMap(event)) {
      ExternalSystemTaskNotificationEvent downloadEvent = myDownloadProgressMapper.map(myTaskId, event);
      if (downloadEvent != null) {
        myListener.onStatusChange(downloadEvent);
        if (sendProgressEventsToOutput) {
          sendProgressEventToOutput(downloadEvent);
        }
        return;
      }
    }
    ExternalSystemTaskNotificationEvent progressBuildEvent = myProgressMapper.map(myTaskId, event);
    if (progressBuildEvent != null) {
      if (!progressBuildEvent.equals(myLastStatusChange)) {
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
      var progressBuildEvent = myProgressMapper.mapLegacyEvent(myTaskId, eventDescription);
      if (progressBuildEvent != null && !progressBuildEvent.equals(myLastStatusChange)) {
        myListener.onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }

      var taskNotificationEvent = GradleProgressEventConverter.legacyConvertTaskNotificationEvent(myTaskId, eventDescription);
      myListener.onStatusChange(taskNotificationEvent);

      if (sendProgressEventsToOutput) {
        reportGradleDaemonStartingEvent(eventDescription);
      }
    }
  }

  private boolean maybeReportModelBuilderMessage(String eventDescription) {
    var message = parseModelBuilderMessage(eventDescription);
    if (message == null) {
      return false;
    }

    myReporter.failure(createGradleIssueFailure(message))
      .withKind(MessageEvent.Kind.valueOf(message.getKind().name()))
      .withInternal(message.isInternal() && message.getKind() == Message.Kind.ERROR)
      .withSuppressed(message.isInternal())
      .withGroup(message.getGroup())
      .withTitle(message.getTitle())
      .withText(message.getText())
      .withTargetPath(ObjectUtils.doIfNotNull(message.getTargetPath(), it -> Path.of(it)))
      .report();
    return true;
  }

  private static @Nullable Message parseModelBuilderMessage(String eventDescription) {
    if (!eventDescription.startsWith(MessageReporter.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX)) {
      return null;
    }
    var messageString = StringUtil.substringAfter(eventDescription, MessageReporter.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX);
    try {
      return new GsonBuilder().create().fromJson(messageString, Message.class);
    }
    catch (Exception e) {
      LOG.warn("Failed to report model builder message using event '" + eventDescription + "'", e);
      return null;
    }
  }

  @VisibleForTesting
  public static @NotNull GradleIssueFailure createGradleIssueFailure(@NotNull Message message) {
    Message.Failure failure = message.getFailure();
    if (failure == null) {
      return GradleIssueFailure.createIssueFailure(message.getTitle(), message.getText());
    }
    return createGradleIssueFailure(failure);
  }

  private static @NotNull GradleIssueFailure createGradleIssueFailure(@NotNull Message.Failure failure) {
    return GradleIssueFailure.createIssueFailure(
      failure.getMessage(),
      failure.getDescription(),
      ContainerUtil.map(failure.getCauses(), GradleProgressListener::createGradleIssueFailure)
    );
  }

  private void sendProgressEventToOutput(ExternalSystemTaskNotificationEvent event) {
    if (event instanceof ExternalSystemBuildEvent) {
      BuildEvent buildEvent = ((ExternalSystemBuildEvent)event).getBuildEvent();
      if (buildEvent instanceof FileDownloadedEventImpl) {
        long duration = ((FileDownloadedEventImpl)buildEvent).getDuration();
        String operationName = buildEvent.getMessage();
        String text = String.format("%s, took %s", operationName, formatDuration(duration));
        myListener.onTaskOutput(myTaskId, "\r" + text + "\n", ProcessOutputType.STDOUT);
      }
      if (buildEvent instanceof FileDownloadEventImpl) {
        long progress = ((FileDownloadEventImpl)buildEvent).getProgress();
        long total = ((FileDownloadEventImpl)buildEvent).getTotal();
        String operationName = buildEvent.getMessage();
        String text = String.format("%s (%s / %s)", operationName, formatFileSize(progress), formatFileSize(total));
        if (((FileDownloadEventImpl)buildEvent).isFirstInGroup()) {
          myListener.onTaskOutput(myTaskId, text, ProcessOutputType.STDOUT);
        }
        else {
          myListener.onTaskOutput(myTaskId, "\r" + text, ProcessOutputType.STDOUT);
        }
      }
    }
  }

  /**
   * Report Gradle Daemon starting event based on the fact that multiple 'Starting Gradle Daemon' might be received when new
   * ProgressLoggerFactory.newOperation are nested within the 'Starting Gradle Daemon' operation reporting always the parent on
   * completion. Based on that, the Build event will be used to calculate when Daemon was started. Those are the events returned:
   *  - Build
   *  - Starting Gradle Daemon
   *  - Discovering toolchains
   *  - Starting Gradle Daemon
   *  - Connecting to Gradle Daemon
   *  - Starting Gradle Daemon
   *  - Build
   */
  private void reportGradleDaemonStartingEvent(String eventDescription) {
    if (StringUtil.equals(STARTING_GRADLE_DAEMON_EVENT, eventDescription) && !myStatusEventIds.containsKey(STARTING_GRADLE_DAEMON_EVENT)) {
      myListener.onTaskOutput(myTaskId, STARTING_GRADLE_DAEMON_EVENT + "...\n", ProcessOutputType.STDOUT);
      myStatusEventIds.put(STARTING_GRADLE_DAEMON_EVENT, System.currentTimeMillis());
    } else if (StringUtil.equals(EXECUTING_BUILD, eventDescription) && myStatusEventIds.containsKey(STARTING_GRADLE_DAEMON_EVENT)) {
      Long startTime = myStatusEventIds.remove(STARTING_GRADLE_DAEMON_EVENT);
      String duration = formatDuration(System.currentTimeMillis() - startTime);
      myListener.onTaskOutput(myTaskId, "\rGradle Daemon started in " + duration + "\n", ProcessOutputType.STDOUT);
    }
  }

  private static @NotNull String formatFileSize(@NotNull Long value) {
    return StringUtil.formatFileSize(value, " ", -1, true);
  }
}
