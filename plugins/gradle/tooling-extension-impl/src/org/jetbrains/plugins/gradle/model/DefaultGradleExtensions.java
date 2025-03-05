// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public class DefaultGradleExtensions implements GradleExtensions {
  private static final long serialVersionUID = 1L;

  private final List<DefaultGradleExtension> extensions;
  private final List<DefaultGradleConvention> conventions;
  private final List<DefaultGradleProperty> gradleProperties;
  private final ArrayList<DefaultExternalTask> tasks;
  private final List<DefaultGradleConfiguration> configurations;
  private String parentProjectPath;

  public DefaultGradleExtensions() {
    extensions = new ArrayList<>(0);
    conventions = new ArrayList<>(0);
    gradleProperties = new ArrayList<>(0);
    tasks = new ArrayList<>(0);
    configurations = new ArrayList<>(0);
  }

  public DefaultGradleExtensions(@NotNull GradleExtensions extensions) {
    parentProjectPath = extensions.getParentProjectPath();

    this.extensions = new ArrayList<>(extensions.getExtensions().size());
    for (GradleExtension extension : extensions.getExtensions()) {
      this.extensions.add(new DefaultGradleExtension(extension));
    }

    conventions = new ArrayList<>(extensions.getConventions().size());
    for (GradleConvention convention : extensions.getConventions()) {
      conventions.add(new DefaultGradleConvention(convention));
    }

    gradleProperties = new ArrayList<>(extensions.getGradleProperties().size());
    for (GradleProperty property : extensions.getGradleProperties()) {
      gradleProperties.add(new DefaultGradleProperty(property));
    }

    tasks = new ArrayList<>(extensions.getTasks().size());
    for (ExternalTask entry : extensions.getTasks()) {
      tasks.add(new DefaultExternalTask(entry));
    }

    configurations = new ArrayList<>(extensions.getConfigurations().size());
    for (GradleConfiguration entry : extensions.getConfigurations()) {
      configurations.add(new DefaultGradleConfiguration(entry));
    }
  }

  @Override
  public @Nullable String getParentProjectPath() {
    return parentProjectPath;
  }

  public void setParentProjectPath(String parentProjectPath) {
    this.parentProjectPath = parentProjectPath;
  }

  @Override
  public @NotNull List<DefaultGradleExtension> getExtensions() {
    return extensions == null ? Collections.emptyList() : extensions;
  }

  @Override
  public @NotNull List<DefaultGradleConvention> getConventions() {
    return conventions == null ? Collections.emptyList() : conventions;
  }

  @Override
  public @NotNull List<DefaultGradleProperty> getGradleProperties() {
    return gradleProperties == null ? Collections.emptyList() : gradleProperties;
  }

  @Override
  public @NotNull List<DefaultExternalTask> getTasks() {
    return tasks;
  }

  public void addTasks(@NotNull Collection<? extends ExternalTask> values) {
    tasks.ensureCapacity(tasks.size() + values.size());
    for (ExternalTask value : values) {
      if (value instanceof DefaultExternalTask) {
        tasks.add((DefaultExternalTask)value);
      }
      else {
        tasks.add(new DefaultExternalTask(value));
      }
    }
  }

  @Override
  public @NotNull List<DefaultGradleConfiguration> getConfigurations() {
    return configurations == null ? Collections.emptyList() : configurations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultGradleExtensions that = (DefaultGradleExtensions)o;

    if (!Objects.equals(extensions, that.extensions)) return false;
    if (!Objects.equals(conventions, that.conventions)) return false;
    if (!Objects.equals(gradleProperties, that.gradleProperties)) return false;
    if (!Objects.equals(tasks, that.tasks)) return false;
    if (!Objects.equals(configurations, that.configurations)) return false;
    if (!Objects.equals(parentProjectPath, that.parentProjectPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = extensions != null ? extensions.hashCode() : 0;
    result = 31 * result + (conventions != null ? conventions.hashCode() : 0);
    result = 31 * result + (gradleProperties != null ? gradleProperties.hashCode() : 0);
    result = 31 * result + (tasks != null ? tasks.hashCode() : 0);
    result = 31 * result + (configurations != null ? configurations.hashCode() : 0);
    result = 31 * result + (parentProjectPath != null ? parentProjectPath.hashCode() : 0);
    return result;
  }
}
