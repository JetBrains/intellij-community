// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingConfig;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingModel;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class AnnotationProcessingModelImpl implements AnnotationProcessingModel, Serializable {
  private final Map<String, AnnotationProcessingConfig> configs;

  public AnnotationProcessingModelImpl(Map<String, AnnotationProcessingConfig> configs) {
    this.configs = configs;
  }

  @Override
  public @NotNull Map<String, AnnotationProcessingConfig> allConfigs() {
    return configs;
  }

  @Override
  public @Nullable AnnotationProcessingConfig bySourceSetName(@NotNull String sourceSetName) {
    return configs.get(sourceSetName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AnnotationProcessingModelImpl model = (AnnotationProcessingModelImpl)o;
    return Objects.equals(configs, model.configs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(configs);
  }
}
