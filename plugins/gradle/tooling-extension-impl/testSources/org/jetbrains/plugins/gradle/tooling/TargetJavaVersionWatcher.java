// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import com.intellij.util.lang.JavaVersion;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetJavaVersion;
import org.jetbrains.plugins.gradle.tooling.util.JavaVersionMatcher;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class TargetJavaVersionWatcher extends TestWatcher {

  @Nullable
  private GradleJvmResolver.VersionRestriction myRestriction;

  public @NotNull GradleJvmResolver.VersionRestriction getRestriction() {
    return myRestriction != null ? myRestriction : GradleJvmResolver.VersionRestriction.NO;
  }

  @Override
  protected void starting(@NotNull Description description) {
    TargetJavaVersion targetJavaVersion = description.getAnnotation(TargetJavaVersion.class);
    if (targetJavaVersion == null) {
      return;
    }
    myRestriction = new GradleJvmResolver.VersionRestriction() {
      @Override
      public boolean isRestricted(@NotNull GradleVersion gradleVersion, @NotNull JavaVersion source) {
        return !JavaVersionMatcher.isVersionMatch(source, targetJavaVersion.value());
      }
    };
  }
}
