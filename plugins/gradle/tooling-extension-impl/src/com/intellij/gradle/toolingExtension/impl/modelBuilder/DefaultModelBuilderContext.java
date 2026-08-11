// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class DefaultModelBuilderContext implements ModelBuilderContext {

  private final Gradle myGradle;
  private final MessageReporter myMessageReporter = new DefaultMessageReporter();
  private final ConcurrentMap<DataProvider<?>, Object> myMap = new ConcurrentHashMap<>();

  public DefaultModelBuilderContext(Gradle gradle) {
    myGradle = gradle;
  }

  @Override
  public @NotNull Gradle getGradle() {
    return myGradle;
  }

  @Override
  public @NotNull MessageReporter getMessageReporter() {
    return myMessageReporter;
  }

  @Override
  public @NotNull <T> T getData(@NotNull DataProvider<T> provider) {
    //noinspection unchecked
    return (T) myMap.computeIfAbsent(provider, p -> p.create(this));
  }
}
