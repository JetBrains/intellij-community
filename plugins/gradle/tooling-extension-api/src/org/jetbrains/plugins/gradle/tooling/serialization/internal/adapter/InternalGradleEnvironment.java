// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.build.GradleEnvironment;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.Serializable;

@ApiStatus.Internal
public final class InternalGradleEnvironment implements GradleEnvironment, Serializable {

  private final File gradleUserHome;
  private final String gradleVersion;

  public InternalGradleEnvironment(File gradleUserHome, String gradleVersion) {
    this.gradleUserHome = gradleUserHome;
    this.gradleVersion = gradleVersion;
  }

  @Override
  public File getGradleUserHome() {
    return gradleUserHome;
  }

  @Override
  public String getGradleVersion() {
    return gradleVersion;
  }
}
