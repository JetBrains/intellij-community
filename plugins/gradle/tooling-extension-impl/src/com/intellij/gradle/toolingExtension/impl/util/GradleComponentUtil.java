// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

public final class GradleComponentUtil {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is45OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("4.5")) >= 0;

  public static @NotNull String getProjectName(@NotNull ProjectComponentIdentifier identifier) {
    return is45OrBetter ? identifier.getProjectName() : identifier.getProjectPath();
  }
}
