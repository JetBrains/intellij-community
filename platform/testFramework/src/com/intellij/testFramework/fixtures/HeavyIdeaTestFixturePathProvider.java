// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Provides a lazy way to determine the target path for a project, for example, for a case where an Application instance is required to
 * be able to determine the target location of a Project.
 */
@FunctionalInterface
public interface HeavyIdeaTestFixturePathProvider {

  /**
   * Location where the project should be created. There are no restrictions on the idempotency.
   */
  @Nullable Path get(@NotNull String testName, @NotNull Disposable disposable);

  default void afterTest(@NotNull String testName) { }
}
