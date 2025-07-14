// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import com.google.gson.GsonBuilder;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReportBuilder;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class DefaultMessageReporter implements MessageReporter {

  private static final Logger LOG = LoggerFactory.getLogger("org.jetbrains.plugins.gradle.toolingExtension.modelBuilder");

  @Override
  public @NotNull MessageReportBuilder createMessage() {
    return new DefaultMessageReportBuilder(this);
  }

  @Override
  public void reportMessage(@NotNull Project project, @NotNull Message message) {
    try {
      if (project instanceof DefaultProject) {
        reportMessageByProgressLogger((DefaultProject)project, message);
      }
      else {
        reportMessageByGlobalLogger(message);
      }
    }
    catch (Throwable e) {
      LOG.warn("Failed to report model builder message", e);
    }
  }

  private static void reportMessageByProgressLogger(@NotNull DefaultProject project, @NotNull Message message) {
    ProgressLoggerFactory progressLoggerFactory = project.getServices().get(ProgressLoggerFactory.class);
    ProgressLogger operation = progressLoggerFactory.newOperation(ModelBuilderService.class);
    String jsonMessage = new GsonBuilder().create().toJson(message);
    operation.setDescription(MODEL_BUILDER_SERVICE_MESSAGE_PREFIX + jsonMessage);
    operation.started();
    operation.completed();
  }

  private static void reportMessageByGlobalLogger(@NotNull Message message) {
    switch (message.getKind()) {
      case ERROR:
        LOG.error(message.getText());
        break;
      case WARNING:
        LOG.warn(message.getText());
        break;
      case INFO:
        LOG.info(message.getText());
        break;
    }
  }
}
