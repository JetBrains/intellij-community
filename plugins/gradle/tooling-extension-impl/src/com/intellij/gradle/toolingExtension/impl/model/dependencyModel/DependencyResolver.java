// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalDependency;

import java.util.Collection;

public interface DependencyResolver {
  String COMPILE_SCOPE = "COMPILE";
  String RUNTIME_SCOPE = "RUNTIME";
  String PROVIDED_SCOPE = "PROVIDED";

  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName);

  Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration);

  Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet);
}