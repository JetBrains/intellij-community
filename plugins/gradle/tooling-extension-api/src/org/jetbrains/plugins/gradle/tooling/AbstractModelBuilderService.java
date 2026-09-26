// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractModelBuilderService implements ModelBuilderService.Ex {
  @Override
  public final Object buildAll(String modelName, Project project) {
    throw new AssertionError("The method should not be called for this service: " + getClass());
  }

  @Override
  public abstract Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context);
}
