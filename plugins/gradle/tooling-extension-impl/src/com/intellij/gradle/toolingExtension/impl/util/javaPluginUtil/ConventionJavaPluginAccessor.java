// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class ConventionJavaPluginAccessor implements JavaPluginAccessor {
  private final Project myProject;

  public ConventionJavaPluginAccessor(Project p) {
    myProject = p;
  }

  private @Nullable JavaPluginConvention getJavaPluginConvention() {
    return myProject.getConvention().findPlugin(JavaPluginConvention.class);
  }


  @Override
  public @Nullable SourceSetContainer getSourceSetContainer() {
    final JavaPluginConvention convention = getJavaPluginConvention();
    return (convention == null ? null : convention.getSourceSets());
  }

  @Override
  public @Nullable String getTargetCompatibility() {
    JavaPluginConvention javaPluginConvention = getJavaPluginConvention();
    if (javaPluginConvention == null) return null;
    return javaPluginConvention.getTargetCompatibility().toString();
  }

  @Override
  public @Nullable String getSourceCompatibility() {
    JavaPluginConvention javaPluginConvention = getJavaPluginConvention();
    if (javaPluginConvention == null) return null;
    return javaPluginConvention.getSourceCompatibility().toString();
  }

  @Override
  public boolean isJavaPluginApplied() {
    return getJavaPluginConvention() != null;
  }
}
