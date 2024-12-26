// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.IdentityHashMap;
import java.util.Map;

@ApiStatus.Internal
public final class DefaultModelBuilderContext implements ModelBuilderContext {

  private final Gradle myGradle;
  private final MessageReporter myMessageReporter = new DefaultMessageReporter();
  private final Map<DataProvider<?>, Object> myMap = new IdentityHashMap<>();

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
}
