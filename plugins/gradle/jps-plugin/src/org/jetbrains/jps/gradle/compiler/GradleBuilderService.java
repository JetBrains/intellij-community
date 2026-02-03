// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.gradle.model.impl.GradleResourcesTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class GradleBuilderService extends BuilderService {
  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    return Arrays.asList(GradleResourcesTargetType.PRODUCTION, GradleResourcesTargetType.TEST);
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Collections.singletonList(new GradleResourcesBuilder());
  }
}
