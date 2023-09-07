// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.artifacts.Configuration;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

public final class GradleConfigurationUtil {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is33OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("3.3")) >= 0;

  public static boolean isConfigurationCanBeResolved(@NotNull Configuration configuration, boolean defaultResult) {
    if (is33OrBetter) {
      return configuration.isCanBeResolved();
    }
    return defaultResult;
  }
}
