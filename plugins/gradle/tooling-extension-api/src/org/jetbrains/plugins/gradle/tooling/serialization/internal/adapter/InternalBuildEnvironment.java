// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.GradleEnvironment;
import org.gradle.tooling.model.build.JavaEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

@ApiStatus.Internal
public final class InternalBuildEnvironment implements BuildEnvironment, Serializable {

  private final Supplier<InternalBuildIdentifier> buildIdentifier;
  private final Supplier<InternalGradleEnvironment> gradle;
  private final Supplier<InternalJavaEnvironment> java;

  public InternalBuildEnvironment(
    @NotNull Supplier<InternalBuildIdentifier> buildIdentifier,
    @NotNull Supplier<InternalGradleEnvironment> gradle,
    @NotNull Supplier<InternalJavaEnvironment> java
  ) {
    this.buildIdentifier = Suppliers.wrap(buildIdentifier);
    this.gradle = Suppliers.wrap(gradle);
    this.java = Suppliers.wrap(java);
  }

  @Override
  public InternalBuildIdentifier getBuildIdentifier() {
    return this.buildIdentifier.get();
  }

  @Override
  public GradleEnvironment getGradle() {
    return gradle.get();
  }

  @Override
  public JavaEnvironment getJava() {
    return java.get();
  }

  @Contract("null -> null")
  public static BuildEnvironment convertBuildEnvironment(@Nullable BuildEnvironment buildEnvironment) {
    if (buildEnvironment == null || buildEnvironment instanceof InternalBuildEnvironment) {
      return buildEnvironment;
    }
    return new InternalBuildEnvironment(
      () -> {
        BuildIdentifier buildIdentifier = buildEnvironment.getBuildIdentifier();
        return new InternalBuildIdentifier(buildIdentifier.getRootDir());
      },
      () -> {
        GradleEnvironment gradle = buildEnvironment.getGradle();
        return new InternalGradleEnvironment(gradle.getGradleUserHome(), gradle.getGradleVersion());
      },
      () -> {
        JavaEnvironment java = buildEnvironment.getJava();
        return new InternalJavaEnvironment(java.getJavaHome(), java.getJvmArguments());
      }
    );
  }
}
