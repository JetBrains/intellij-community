// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.google.gson.GsonBuilder;
import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.events.impl.FileDownloadEventImpl;
import com.intellij.build.events.impl.FileDownloadedEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker;
import org.jetbrains.plugins.gradle.issue.GradleIssueData;
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure;
import org.jetbrains.plugins.gradle.statistics.GradleModelBuilderMessageCollector;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Files;
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
  private final GradleExecutionProgressMapper myProgressMapper;
  private final Map<Object, Long> myStatusEventIds = new HashMap<>();
  private final String myOperationId;
  private final @NotNull GradleExecutionContext myContext;
  private static final String EXECUTING_BUILD = "Build";
  private static final String STARTING_GRADLE_DAEMON_EVENT = "Starting Gradle Daemon";
  private ExternalSystemTaskNotificationEvent myLastStatusChange = null;
  private final boolean sendProgressEventsToOutput;

  public GradleProgressListener(
    @NotNull GradleExecutionContext context,
    @NotNull String buildRootDir
  ) {
    myContext = context;
    myOperationId = context.getTaskId().hashCode() + ":" + FileUtil.pathHashCode(buildRootDir);
    myProgressMapper = new GradleExecutionProgressMapper();
    myDownloadProgressMapper = new GradleDownloadProgressMapper();
    sendProgressEventsToOutput = Registry.is(SEND_PROGRESS_EVENTS_TO_OUTPUT_KEY, true);
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    if (myDownloadProgressMapper.canMap(event)) {
      ExternalSystemTaskNotificationEvent downloadEvent = myDownloadProgressMapper.map(myContext.getTaskId(), event);
      if (downloadEvent != null) {
        myContext.getListener().onStatusChange(downloadEvent);
        if (sendProgressEventsToOutput) {
          sendProgressEventToOutput(downloadEvent);
        }
        return;
      }
    }
    ExternalSystemTaskNotificationEvent progressBuildEvent = myProgressMapper.map(myContext.getTaskId(), event);
    if (progressBuildEvent != null) {
      if (!progressBuildEvent.equals(myLastStatusChange)) {
        myContext.getListener().onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }
    }
    var taskNotificationEvent = GradleProgressEventConverter.createTaskNotificationEvent(myContext.getTaskId(), myOperationId, event);
    if (taskNotificationEvent != null) {
      myContext.getListener().onStatusChange(taskNotificationEvent);
    }
  }

  @Override
  public void statusChanged(org.gradle.tooling.ProgressEvent event) {
    var eventDescription = event.getDescription();
    if (!maybeReportModelBuilderMessage(eventDescription)) {
      var progressBuildEvent = myProgressMapper.mapLegacyEvent(myContext.getTaskId(), eventDescription);
      if (progressBuildEvent != null && !progressBuildEvent.equals(myLastStatusChange)) {
        myContext.getListener().onStatusChange(progressBuildEvent);
        myLastStatusChange = progressBuildEvent;
      }

      var taskNotificationEvent = GradleProgressEventConverter.legacyConvertTaskNotificationEvent(myContext.getTaskId(), eventDescription);
      myContext.getListener().onStatusChange(taskNotificationEvent);

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
    GradleModelBuilderMessageCollector.logModelBuilderMessage(myContext.getTaskId().findProject(), myContext.getTaskId().getId(), message);
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
      BuildEvent messageEvent = getModelBuilderIssueOrMessage(message);
      myContext.getListener().onStatusChange(new ExternalSystemBuildEvent(myContext.getTaskId(), messageEvent));
    }
  }

  private @NotNull MessageEvent getModelBuilderMessage(@NotNull Message message) {
    MessageEvent.Kind kind = MessageEvent.Kind.valueOf(message.getKind().name());
    FilePosition filePosition = getModelBuilderMessagePosition(message);
    return new MessageEventImpl(
      myContext.getTaskId(),
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

  private static @Nullable FilePosition getModelBuilderMessagePosition(@NotNull Message message) {
    var targetPath = ObjectUtils.doIfNotNull(message.getTargetPath(), it -> it.toPath());
    if (targetPath == null) return null;
    var targetBuildFile = GradleConstants.KNOWN_GRADLE_FILES.stream()
      .map(it -> targetPath.resolve(it))
      .filter(it -> Files.isRegularFile(it))
      .findFirst().orElse(null);
    if (targetBuildFile == null) return null;
    return new FilePosition(targetBuildFile, 0, 0);
  }

  /**
   * Transforms a model builder message into a BuildIssue by delegating to known Gradle issue checkers.
   * This allows issue checkers to provide quick fixes and internationalized descriptions for messages of any severity.
   */
  private @NotNull BuildEvent getModelBuilderIssueOrMessage(@NotNull Message message) {
    // Build a synthetic GradleIssueData from the message
    GradleIssueData issueData = createGradleIssueFailure(message);

    for (var checker : GradleIssueChecker.getKnownIssuesCheckList()) {
      BuildIssue buildIssue = checker.check(issueData);
      if (buildIssue != null) {
        MessageEvent.Kind kind = MessageEvent.Kind.valueOf(message.getKind().name());
        return new BuildIssueEventImpl(myContext.getTaskId(), buildIssue, kind);
      }
    }

    // Fallback to a regular message event if no issue checker matched
    return getModelBuilderMessage(message);
  }

  private @NotNull GradleIssueData createGradleIssueData(@NotNull Message message) {
    GradleIssueFailure failure = createGradleIssueFailure(message);
    return GradleIssueData.createIssueData(getBuildRoot(myContext), failure, null, null);
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
        myContext.getListener().onTaskOutput(myContext.getTaskId(), "\r" + text + "\n", ProcessOutputType.STDOUT);
      }
      if (buildEvent instanceof FileDownloadEventImpl) {
        long progress = ((FileDownloadEventImpl)buildEvent).getProgress();
        long total = ((FileDownloadEventImpl)buildEvent).getTotal();
        String operationName = buildEvent.getMessage();
        String text = String.format("%s (%s / %s)", operationName, formatFileSize(progress), formatFileSize(total));
        if (((FileDownloadEventImpl)buildEvent).isFirstInGroup()) {
          myContext.getListener().onTaskOutput(myContext.getTaskId(), text, ProcessOutputType.STDOUT);
        }
        else {
          myContext.getListener().onTaskOutput(myContext.getTaskId(), "\r" + text, ProcessOutputType.STDOUT);
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
      myContext.getListener().onTaskOutput(myContext.getTaskId(), STARTING_GRADLE_DAEMON_EVENT + "...\n", ProcessOutputType.STDOUT);
      myStatusEventIds.put(STARTING_GRADLE_DAEMON_EVENT, System.currentTimeMillis());
    } else if (StringUtil.equals(EXECUTING_BUILD, eventDescription) && myStatusEventIds.containsKey(STARTING_GRADLE_DAEMON_EVENT)) {
      Long startTime = myStatusEventIds.remove(STARTING_GRADLE_DAEMON_EVENT);
      String duration = formatDuration(System.currentTimeMillis() - startTime);
      myContext.getListener().onTaskOutput(myContext.getTaskId(), "\rGradle Daemon started in " + duration + "\n", ProcessOutputType.STDOUT);
    }
  }

  private static @NotNull Path getBuildRoot(@NotNull GradleExecutionContextImpl context) {
    BuildEnvironment buildEnvironment = context.getBuildEnvironmentOrNull();
    return buildEnvironment == null ? Path.of(context.getProjectPath()) : buildEnvironment.getBuildIdentifier().getRootDir().toPath();
  }

  private static @NotNull String formatFileSize(@NotNull Long value) {
    return StringUtil.formatFileSize(value, " ", -1, true);
  }
}
