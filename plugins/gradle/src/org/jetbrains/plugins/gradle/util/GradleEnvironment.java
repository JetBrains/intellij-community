// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleEnvironment {

  public static final @NonNls boolean DEBUG_GRADLE_HOME_PROCESSING = Boolean.getBoolean("gradle.debug.home.processing");

  public static final class Headless {
    public static final @NonNls String GRADLE_DISTRIBUTION_TYPE = System.getProperty("idea.gradle.distributionType");
    public static final @NonNls String GRADLE_HOME = System.getProperty("idea.gradle.home");
    public static final @NonNls String GRADLE_VM_OPTIONS = System.getProperty("idea.gradle.vmOptions");
    public static final @NonNls String GRADLE_OFFLINE = System.getProperty("idea.gradle.offline");
    public static final @NonNls String GRADLE_SERVICE_DIRECTORY = System.getProperty("idea.gradle.serviceDirectory");
  }

  @ApiStatus.Internal
  public static final class Urls {

    public static final @Nullable String MAVEN_REPOSITORY_URL =
      System.getProperty("idea.gradle.mavenRepositoryUrl", null);

    public static final @NotNull String GRADLE_SERVICES_URL =
      System.getProperty("idea.gradle.servicesUrl", "https://services.gradle.org");
  }
}
