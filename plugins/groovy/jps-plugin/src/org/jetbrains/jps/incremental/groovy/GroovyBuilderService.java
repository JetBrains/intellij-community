// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GroovyBuilderService extends BuilderService {

  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    return CheckResourcesTarget.TARGET_TYPES;
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Collections.singletonList(new GroovyResourceChecker());
  }

  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new GroovyBuilder(true), new GroovyBuilder(false), new GreclipseBuilder());
  }
}
