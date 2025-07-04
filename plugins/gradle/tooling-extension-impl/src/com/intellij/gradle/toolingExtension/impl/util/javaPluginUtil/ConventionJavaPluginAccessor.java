// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil;

import com.intellij.gradle.toolingExtension.impl.util.GradleConventionUtil;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.gradle.toolingExtension.impl.util.GradleConventionUtil.JAVA_PLUGIN_CONVENTION_CLASS_FQDN;
import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.getValue;

public class ConventionJavaPluginAccessor implements JavaPluginAccessor {

  private final @NotNull Project myProject;

  public ConventionJavaPluginAccessor(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @Nullable SourceSetContainer getSourceSetContainer() {
    Object javaPluginConvention = getJavaPluginConvention();
    if (javaPluginConvention == null) {
      return null;
    }
    return getValue(javaPluginConvention, "getSourceSets", SourceSetContainer.class);
  }

  @Override
  public @Nullable String getTargetCompatibility() {
    Object javaPluginConvention = getJavaPluginConvention();
    if (javaPluginConvention == null) {
      return null;
    }
    return getValue(javaPluginConvention, "getTargetCompatibility", JavaVersion.class)
      .toString();
  }

  @Override
  public @Nullable String getSourceCompatibility() {
    Object javaPluginConvention = getJavaPluginConvention();
    if (javaPluginConvention == null) {
      return null;
    }
    return getValue(javaPluginConvention, "getSourceCompatibility", JavaVersion.class)
      .toString();
  }

  @Override
  public boolean isJavaPluginApplied() {
    return getJavaPluginConvention() != null;
  }

  private @Nullable /*org.gradle.api.plugins.JavaPluginConvention*/ Object getJavaPluginConvention() {
    if (!GradleConventionUtil.isGradleConventionsSupported()) {
      throw new IllegalStateException("ConventionJavaPluginAccessor should not be used for accessing Gradle conventions. " +
                                      "Gradle of version " + GradleVersion.current().getVersion() + " does not support conventions");
    }
    return GradleConventionUtil.findConventionPlugin(myProject, JAVA_PLUGIN_CONVENTION_CLASS_FQDN);
  }
}
