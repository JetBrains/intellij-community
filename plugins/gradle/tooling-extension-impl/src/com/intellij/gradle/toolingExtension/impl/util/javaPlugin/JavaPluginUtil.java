// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPlugin;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

public final class JavaPluginUtil {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is82OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("8.2")) >= 0;

  @NotNull
  public static JavaPluginAccessor getJavaPluginAccessor(@NotNull Project p) {
    if (is82OrBetter) {
      return new ExtensionJavaPluginAccessor(p);
    }
    else {
      return new ConventionJavaPluginAccessor(p);
    }
  }
}
