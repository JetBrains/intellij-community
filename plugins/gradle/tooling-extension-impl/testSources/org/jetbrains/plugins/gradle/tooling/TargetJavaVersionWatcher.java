// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetJavaVersion;
import org.jetbrains.plugins.gradle.tooling.jvm.GradleBrokenJvmSerialisationVersionRestriction;
import org.jetbrains.plugins.gradle.tooling.jvm.GradleToolingApi9VersionRestriction;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.List;

public class TargetJavaVersionWatcher extends TestWatcher {

  @Nullable
  private JavaVersionRestriction myRestriction;

  public TargetJavaVersionWatcher() {
    this(null);
  }

  public TargetJavaVersionWatcher(@Nullable JavaVersionRestriction restriction) {
    List<JavaVersionRestriction> effectiveRestrictions = new ArrayList<>();
    effectiveRestrictions.add(new GradleToolingApi9VersionRestriction());
    effectiveRestrictions.add(new GradleBrokenJvmSerialisationVersionRestriction());
    if (restriction != null) {
      effectiveRestrictions.add(restriction);
    }
    myRestriction = JavaVersionRestriction.compositeOf(effectiveRestrictions);
  }

  public @NotNull JavaVersionRestriction getRestriction() {
    return myRestriction != null ? myRestriction : JavaVersionRestriction.NO;
  }

  @Override
  protected void starting(@NotNull Description description) {
    TargetJavaVersion targetJavaVersion = description.getAnnotation(TargetJavaVersion.class);
    if (targetJavaVersion == null) {
      return;
    }
    myRestriction = JavaVersionRestriction.javaRestrictionOf(targetJavaVersion.value());
  }
}
