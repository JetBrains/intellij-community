// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileDownloadEventImpl;
import com.intellij.build.events.impl.FileDownloadedEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.google.gson.GsonBuilder;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.statistics.GradleModelBuilderMessageCollector;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.formatDuration;

/**
 * @author Vladislav.Soroka
 */
public class GradleProgressListener implements ProgressListener, org.gradle.tooling.ProgressListener {
  private static final Logger LOG = Logger.getInstance(GradleProgressListener.class);
  public static final String SEND_PROGRESS_EVENTS_TO_OUTPUT_KEY = "gradle.output.sync.progress.events";
  private final GradleDownloadProgressMapper myDownloadProgressMapper;
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
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull ExternalSystemTaskId taskId
  ) {
    this(listener, taskId, null);
  }

  public GradleProgressListener(
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull ExternalSystemTaskId taskId,
    @Nullable Path buildRootDir
  ) {
    myListener = listener;
    myTaskId = taskId;
    myOperationId = taskId.hashCode() + ":" + FileUtil.pathHashCode(buildRootDir == null ? UUID.randomUUID().toString() : buildRootDir.toString());
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

    reportModelBuilderMessageToFus(message);
    reportModelBuilderMessageToLogger(message);
    reportModelBuilderMessageToListener(message);
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

  private void reportModelBuilderMessageToFus(@NotNull Message message) {
    GradleModelBuilderMessageCollector.logModelBuilderMessage(myTaskId.findProject(), myTaskId.getId(), message);
  }

  private static void reportModelBuilderMessageToLogger(@NotNull Message message) {
    var text = message.getGroup() + "\n" +
               message.getTitle() + "\n" +
               message.getText();
    if (message.isInternal() && message.getKind() == Message.Kind.ERROR) {
      LOG.error(text, new Throwable());
    }
    else {
      LOG.debug(text);
    }
  }

  private void reportModelBuilderMessageToListener(@NotNull Message message) {
    if (!message.isInternal()) {
      var messageEvent = getModelBuilderMessage(message);
      myListener.onStatusChange(new ExternalSystemBuildEvent(myTaskId, messageEvent));
    }
  }

  private @NotNull MessageEvent getModelBuilderMessage(@NotNull Message message) {
    MessageEvent.Kind kind = MessageEvent.Kind.valueOf(message.getKind().name());
    Message.FilePosition messageFilePosition = message.getFilePosition();
    FilePosition filePosition = messageFilePosition == null ? null : new FilePosition(
      new File(messageFilePosition.getFilePath()),
      messageFilePosition.getLine(),
      messageFilePosition.getColumn()
    );
    return new MessageEventImpl(
      myTaskId,
      kind,
      message.getGroup(),
      message.getTitle(),
      message.getText()
    ) {
      @Override
      public @Nullable Navigatable getNavigatable(@NotNull Project project) {
        if (filePosition == null) return null;
        return new FileNavigatable(project, filePosition);
      }
    };
  }

  private void sendProgressEventToOutput(ExternalSystemTaskNotificationEvent event) {
    if (event instanceof ExternalSystemBuildEvent) {
      BuildEvent buildEvent = ((ExternalSystemBuildEvent)event).getBuildEvent();
      if (buildEvent instanceof FileDownloadedEventImpl) {
        long duration = ((FileDownloadedEventImpl)buildEvent).getDuration();
        String operationName = buildEvent.getMessage();
        String text = String.format("%s, took %s", operationName, formatDuration(duration));
        myListener.onTaskOutput(myTaskId, "\r" + text + "\n", true);
      }
      if (buildEvent instanceof FileDownloadEventImpl) {
        long progress = ((FileDownloadEventImpl)buildEvent).getProgress();
        long total = ((FileDownloadEventImpl)buildEvent).getTotal();
        String operationName = buildEvent.getMessage();
        String text = String.format("%s (%s / %s)", operationName, formatFileSize(progress), formatFileSize(total));
        if (((FileDownloadEventImpl)buildEvent).isFirstInGroup()) {
          myListener.onTaskOutput(myTaskId, text, true);
        }
        else {
          myListener.onTaskOutput(myTaskId, "\r" + text, true);
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
      myListener.onTaskOutput(myTaskId, STARTING_GRADLE_DAEMON_EVENT + "...\n", true);
      myStatusEventIds.put(STARTING_GRADLE_DAEMON_EVENT, System.currentTimeMillis());
    } else if (StringUtil.equals(EXECUTING_BUILD, eventDescription) && myStatusEventIds.containsKey(STARTING_GRADLE_DAEMON_EVENT)) {
      Long startTime = myStatusEventIds.remove(STARTING_GRADLE_DAEMON_EVENT);
      String duration = formatDuration(System.currentTimeMillis() - startTime);
      myListener.onTaskOutput(myTaskId, "\rGradle Daemon started in " + duration + "\n", true);
    }
  }

  private static @NotNull String formatFileSize(@NotNull Long value) {
    return StringUtil.formatFileSize(value, " ", -1, true);
  }
}
