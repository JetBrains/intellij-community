// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.IdentityHashMap;
import java.util.Map;

public final class DefaultModelBuilderContext implements ModelBuilderContext {

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

  @Override
  public void report(@NotNull Project project, @NotNull Message message) {
    new DefaultMessageReporter().report(project, message);
  }
}
