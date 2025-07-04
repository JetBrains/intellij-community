// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion

object GradleVersionSpecificsUtil {
  /**
   * Before Gradle 8.0, buildSrc build is synced separately from the build it belongs to, so GradleLightBuild#getParentBuild == null for it.
   * Since Gradle 8.0, buildSrc has become a part of includedBuilds of the build it belongs to, so getParentBuild is not null anymore.
   */
  fun isBuildSrcSyncedSeparately(gradleVersion: GradleVersion): Boolean =
    GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")
}