// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nullable;

import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.getValue;

class ConventionApplicationPluginAccessor implements ApplicationPluginAccessor {

  private final Project myGradleProject;

  ConventionApplicationPluginAccessor(Project gradleProject) {
    myGradleProject = gradleProject;
  }

  @Override
  public @Nullable String getMainClass() {
    if (GradleConventionUtil.isGradleConventionsSupported()) {
      Object applicationPlugin = GradleConventionUtil.findConventionPlugin(myGradleProject, GradleConventionUtil.APPLICATION_PLUGIN_CONVENTION_CLASS_FQDN);
      if (applicationPlugin == null) {
        return null;
      }
      return getValue(applicationPlugin, "getMainClassName", String.class);
    }
    throw new IllegalStateException(
      "ConventionApplicationPluginAccessor shouldn't be used for Gradle " + GradleVersion.current().getBaseVersion()
    );
  }
}
