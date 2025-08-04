// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.util.GradleVersion;
import org.hamcrest.CoreMatchers;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportDefaultDataKt;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Arrays;
import java.util.List;

public class VersionMatcherRule extends TestWatcher {

  /**
   * Note: When adding new versions here, change also lists:<br/>
   * - Idea_Tests_BuildToolsTests<br/>
   * - IntelliJ TeamCity configuration<br/>
   * - {@link VersionMatcherRule#BASE_GRADLE_VERSION}<br/>
   * - {@link GradleJvmSupportDefaultDataKt#getDEFAULT_DATA()}<br/>
   * - "gradle.versions.list" file in the resources<br/>
   */
  public static final String[] SUPPORTED_GRADLE_VERSIONS = {
    "4.5.1", /*"4.6", "4.7", "4.8", "4.9",*/ "4.10.3",
    "5.0", /*"5.1", "5.2", "5.3.1", "5.4.1", "5.5.1",*/ "5.6.2",
    "6.0", /* "6.0.1",  "6.1", "6.2", "6.3", "6.4", "6.8.3",*/ "6.9",
    "7.0.2", /* "7.1", "7.2", "7.4", "7.5.1",*/ "7.6",
    "8.0", /*"8.2", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10", "8.11", "8.12", "8.13",*/ "8.14.1",
    "9.0.0"
  };
  public static final String BASE_GRADLE_VERSION = "9.0.0";

  @Nullable
  private CustomMatcher<String> myMatcher;

  @NotNull
  public Matcher<String> getMatcher() {
    return myMatcher != null ? myMatcher : CoreMatchers.any(String.class);
  }

  @Override
  protected void starting(Description d) {
    final TargetVersions targetVersions = d.getAnnotation(TargetVersions.class);
    if (targetVersions == null) return;

    myMatcher = new CustomMatcher<String>("Gradle version '" + Arrays.toString(targetVersions.value()) + "'") {
      @Override
      public boolean matches(Object item) {
        return item instanceof String && new VersionMatcher(GradleVersion.version(item.toString())).isVersionMatch(targetVersions);
      }
    };
  }

  public static @NotNull List<String> getSupportedGradleVersions() {
    String gradleVersionsString = System.getProperty("gradle.versions.to.run");
    if (gradleVersionsString != null && !gradleVersionsString.isEmpty()) {
      if (gradleVersionsString.startsWith("LAST:")) {
        int last = Integer.parseInt(gradleVersionsString.substring("LAST:".length()));
        List<String> all = Arrays.asList(SUPPORTED_GRADLE_VERSIONS);
        return all.subList(all.size() - last, all.size());
      }
      else {
        String[] gradleVersionsToRun = gradleVersionsString.split(",");
        return Arrays.asList(gradleVersionsToRun);
      }
    }
    return Arrays.asList(SUPPORTED_GRADLE_VERSIONS);
  }
}
