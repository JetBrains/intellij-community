// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

public final class JavaPluginUtil {

  @NotNull
  public static JavaPluginAccessor getJavaPluginAccessor(@NotNull Project p) {
    if (canAccessConventions(p)) {
      return new ConventionJavaPluginAccessor(p);
    } else {
      return new ExtensionJavaPluginAccessor(p);
    }
  }

  private static boolean canAccessConventions(@NotNull Project p) {
    return GradleVersion.version(p.getGradle().getGradleVersion()).getBaseVersion().compareTo(GradleVersion.version("8.2")) < 0;
  }
}
