// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class DefaultGradleConfiguration implements GradleConfiguration {
  private static final long serialVersionUID = 2L;

  private final String name;
  private final String description;
  private final boolean visible;
  private final boolean scriptClasspathConfiguration;
  private final List<String> declarationAlternatives;


  @PropertyMapping({"name", "description", "visible", "declarationAlternatives"})
  public DefaultGradleConfiguration(String name, String description, boolean visible, @NotNull List<String> declarationAlternatives) {
    this(name, description, visible, false, declarationAlternatives);
  }

  public DefaultGradleConfiguration(@NotNull String name, @Nullable String description, boolean visible, boolean scriptClasspathConfiguration, @NotNull List<String> declarationAlternatives) {
    this.name = name;
    this.description = description;
    this.visible = visible;
    this.scriptClasspathConfiguration = scriptClasspathConfiguration;
    this.declarationAlternatives = declarationAlternatives;
  }

  public DefaultGradleConfiguration(GradleConfiguration configuration) {
    this(configuration.getName(), configuration.getDescription(), configuration.isVisible(),
         configuration.isScriptClasspathConfiguration(),
         configuration.getDeclarationAlternatives());
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @Nullable String getDescription() {
    return description;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  @Override
  public boolean isScriptClasspathConfiguration() {
    return scriptClasspathConfiguration;
  }

  @Override
  public @NotNull List<String> getDeclarationAlternatives() {
    return declarationAlternatives;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultGradleConfiguration that = (DefaultGradleConfiguration)o;

    if (visible != that.visible) return false;
    if (scriptClasspathConfiguration != that.scriptClasspathConfiguration) return false;
    if (!Objects.equals(name, that.name)) return false;
    if (!Objects.equals(description, that.description)) return false;
    if (!declarationAlternatives.equals(that.declarationAlternatives)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (visible ? 1 : 0);
    result = 31 * result + (scriptClasspathConfiguration ? 1 : 0);
    result = 31 * result + (declarationAlternatives.hashCode());
    return result;
  }
}
