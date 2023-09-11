// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.impldep.com.google.gson.GsonBuilder;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.ExtraModelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

public final class DefaultModelBuilderContext implements ModelBuilderContext {

  private static final Logger LOG = LoggerFactory.getLogger("org.jetbrains.plugins.gradle.toolingExtension.modelBuilder");

  private final Map<DataProvider<?>, Object> myMap = new IdentityHashMap<>();
  private final Gradle myGradle;

  public DefaultModelBuilderContext(Gradle gradle) {
    myGradle = gradle;
  }

  @Override
  public @NotNull Gradle getGradle() {
    return myGradle;
  }

  @NotNull
  @Override
  public <T> T getData(@NotNull DataProvider<T> provider) {
    Object data = myMap.get(provider);
    if (data == null) {
      synchronized (myMap) {
        Object secondAttempt = myMap.get(provider);
        if (secondAttempt != null) {
          //noinspection unchecked
          return (T)secondAttempt;
        }
        T value = provider.create(this);
        myMap.put(provider, value);
        return value;
      }
    }
    else {
      //noinspection unchecked
      return (T)data;
    }
  }

  @ApiStatus.Experimental
  @Override
  public void report(@NotNull Project project, @NotNull Message message) {
    if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("2.14.1")) < 0) {
      return;
    }
    try {
      ProgressLoggerFactory progressLoggerFactory = ((DefaultProject)project).getServices().get(ProgressLoggerFactory.class);
      ProgressLogger operation = progressLoggerFactory.newOperation(ModelBuilderService.class);
      String jsonMessage = new GsonBuilder().create().toJson(message);
      operation.setDescription(ExtraModelBuilder.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX + jsonMessage);
      operation.started();
      operation.completed();
    }
    catch (Throwable e) {
      LOG.warn("Failed to report model builder message", e);
    }
  }
}
