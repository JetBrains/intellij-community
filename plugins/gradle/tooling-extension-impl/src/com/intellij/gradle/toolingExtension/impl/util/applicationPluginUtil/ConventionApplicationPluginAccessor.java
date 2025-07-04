// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.applicationPluginUtil;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nullable;

import static com.intellij.gradle.toolingExtension.impl.util.GradleConventionUtil.*;
import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.getValue;

public class ConventionApplicationPluginAccessor implements ApplicationPluginAccessor {

  private final Project myGradleProject;

  public ConventionApplicationPluginAccessor(Project gradleProject) {
    myGradleProject = gradleProject;
  }

  @Override
  public @Nullable String getMainClass() {
    if (isGradleConventionsSupported()) {
      Object applicationPlugin = findConventionPlugin(myGradleProject, APPLICATION_PLUGIN_CONVENTION_CLASS_FQDN);
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
