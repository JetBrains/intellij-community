// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.applicationPluginUtil;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaApplication;
import org.jetbrains.annotations.Nullable;

public class ExtensionApplicationPluginAccessor implements ApplicationPluginAccessor {

  private final Project myGradleProject;

  public ExtensionApplicationPluginAccessor(Project gradleProject) {
    myGradleProject = gradleProject;
  }

  @Override
  public @Nullable String getMainClass() {
    JavaApplication javaApplicationExtension = myGradleProject.getExtensions().findByType(JavaApplication.class);
    if (javaApplicationExtension == null) return null;
    return javaApplicationExtension.getMainClass().get();
  }
}
