// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.Nullable;

public class ExtensionJavaPluginAccessor implements JavaPluginAccessor {
  private final Project myProject;

  public ExtensionJavaPluginAccessor(Project p) {
    myProject = p;
  }

  @Nullable
  private JavaPluginExtension getJavaPluginExtension() {
    return myProject.getExtensions().findByType(JavaPluginExtension.class);
  }


  @Override
  @Nullable
  public SourceSetContainer getSourceSetContainer() {
    JavaPluginExtension javaExtension = getJavaPluginExtension();
    if (javaExtension != null) {
      return javaExtension.getSourceSets();
    }
    return null;
  }

  @Override
  @Nullable
  public String getTargetCompatibility() {
    JavaPluginExtension javaExtension = getJavaPluginExtension();
    if (javaExtension != null) {
      return javaExtension.getTargetCompatibility().toString();
    }
    return null;
  }

  @Override
  @Nullable
  public String getSourceCompatibility() {
    JavaPluginExtension javaExtension = getJavaPluginExtension();
    if (javaExtension != null) {
      return javaExtension.getSourceCompatibility().toString();
    }
    return null;
  }

  @Override
  public boolean isJavaPluginApplied() {
    return getJavaPluginExtension() != null;
  }
}
