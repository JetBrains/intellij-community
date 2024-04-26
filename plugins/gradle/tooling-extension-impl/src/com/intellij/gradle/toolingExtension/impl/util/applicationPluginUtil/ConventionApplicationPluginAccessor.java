// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.applicationPluginUtil;

import org.gradle.api.Project;
import org.gradle.api.plugins.ApplicationPluginConvention;
import org.jetbrains.annotations.Nullable;

public class ConventionApplicationPluginAccessor implements ApplicationPluginAccessor {

  private final Project myGradleProject;

  public ConventionApplicationPluginAccessor(Project gradleProject) {
    myGradleProject = gradleProject;
  }

  @Override
  public @Nullable String getMainClass() {
    ApplicationPluginConvention applicationPluginConvention = myGradleProject.getConvention().findPlugin(ApplicationPluginConvention.class);
    if (applicationPluginConvention == null) return null;
    return applicationPluginConvention.getMainClassName();
  }
}
