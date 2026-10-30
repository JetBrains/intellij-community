// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.jvm

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleAtLeast
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleOlderThan
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion

// https://github.com/gradle/gradle/issues/9339
class GradleBrokenJvmSerialisationVersionRestriction : JavaVersionRestriction {
  override fun isRestricted(gradleVersion: GradleVersion, source: JavaVersion): Boolean {
    return (isGradleAtLeast(gradleVersion, "5.6") && isGradleOlderThan(gradleVersion, "7.3")) && source.feature < 11
  }
}