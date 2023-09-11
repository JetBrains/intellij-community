// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.internal.impldep.com.google.gson.GsonBuilder;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public class DefaultMessageReporter implements MessageReporter {

  public static final String MODEL_BUILDER_SERVICE_MESSAGE_PREFIX = "ModelBuilderService message: ";

  private static final Logger LOG = LoggerFactory.getLogger("org.jetbrains.plugins.gradle.toolingExtension.modelBuilder");

  @Override
  public void report(@NotNull Project project, @NotNull Message message) {
    if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("2.14.1")) < 0) {
      return;
    }
    try {
      ProgressLoggerFactory progressLoggerFactory = ((DefaultProject)project).getServices().get(ProgressLoggerFactory.class);
      ProgressLogger operation = progressLoggerFactory.newOperation(ModelBuilderService.class);
      String jsonMessage = new GsonBuilder().create().toJson(message);
      operation.setDescription(MODEL_BUILDER_SERVICE_MESSAGE_PREFIX + jsonMessage);
      operation.started();
      operation.completed();
    }
    catch (Throwable e) {
      LOG.warn("Failed to report model builder message", e);
    }
  }
}
