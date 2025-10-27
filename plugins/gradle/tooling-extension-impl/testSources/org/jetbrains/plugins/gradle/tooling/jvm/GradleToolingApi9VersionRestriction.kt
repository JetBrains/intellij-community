// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.jvm

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction

class GradleToolingApi9VersionRestriction : JavaVersionRestriction {
  override fun isRestricted(gradleVersion: GradleVersion, source: JavaVersion): Boolean {
    return GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.3") && source.feature < 17
  }
}