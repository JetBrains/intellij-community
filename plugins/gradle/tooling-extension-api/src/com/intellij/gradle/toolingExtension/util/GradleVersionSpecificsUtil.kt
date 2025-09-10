// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util

import org.gradle.util.GradleVersion

object GradleVersionSpecificsUtil {
  /**
   * Before Gradle 8.0, buildSrc build is synced separately from the build it belongs to, so GradleLightBuild#getParentBuild == null for it.
   * Since Gradle 8.0, buildSrc has become a part of includedBuilds of the build it belongs to, so getParentBuild is not null anymore.
   */
  @JvmStatic
  fun isBuildSrcSyncedSeparately(gradleVersion: GradleVersion): Boolean =
    GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")

  /**
   * In the 8.2 version, Gradle introduced BasicGradleProject#getBuildTreePath, which is used as an identity path for GradleLightProject.
   * Before Gradle 8.2, the identity path is calculated in IDEA in DefaultGradleLightProject#getProjectIdentityPath.
   *
   * [Gradle documentation for getBuildTreePath](https://docs.gradle.org/current/javadoc/org/gradle/tooling/model/gradle/BasicGradleProject.html#getBuildTreePath())
   */
  @JvmStatic
  fun isBuildTreePathAvailable(gradleVersion: GradleVersion): Boolean =
    GradleVersionUtil.isGradleAtLeast(gradleVersion, "8.2")

  /**
   * Since 7.2, Gradle adds `buildSrc` builds to `editableBuilds` of the root build:
   * - `buildSrc` belonging to the root build
   * - also, all `buildSrc` belonging to all transitive included builds of the root build
   *
   * [Gradle documentation for getEditableBuilds](https://docs.gradle.org/current/javadoc/org/gradle/tooling/model/gradle/GradleBuild.html#getEditableBuilds())
   */
  @JvmStatic
  fun isBuildSrcAddedInEditableBuilds(gradleVersion: GradleVersion): Boolean =
    GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.2")
}