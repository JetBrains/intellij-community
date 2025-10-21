// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.*
import org.junit.jupiter.api.Assumptions


fun assumeThatGradleIsAtLeast(gradleVersion: GradleVersion, version: String) {
  assumeThatGradleIsAtLeast(gradleVersion, version) {
    "Test cannot be executed on Gradle versions older than $version."
  }
}

fun assumeThatGradleIsAtLeast(gradleVersion: GradleVersion, version: String, lazyMessage: () -> String) {
  Assumptions.assumeTrue(GradleVersionUtil.isGradleAtLeast(gradleVersion, version), lazyMessage)
}

fun assumeThatGradleIsOlderThan(gradleVersion: GradleVersion, version: String) {
  assumeThatGradleIsOlderThan(gradleVersion, version) {
    "Test cannot be executed on Gradle versions newer than $version."
  }
}

fun assumeThatGradleIsOlderThan(gradleVersion: GradleVersion, version: String, lazyMessage: () -> String) {
  Assumptions.assumeTrue(GradleVersionUtil.isGradleOlderThan(gradleVersion, version), lazyMessage)
}

fun assumeThatJunit5IsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isJunit5Supported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support Junit 5."
  }
}

fun assumeThatKotlinIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isKotlinSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support Kotlin."
  }
}

fun assumeThatSpockIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isSpockSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support Spock."
  }
}

fun assumeThatRobolectricIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isRobolectricSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support Robolectric."
  }
}

fun assumeThatTopLevelJavaConventionsIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isTopLevelJavaConventionsSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support top-level java conventions."
  }
}

fun assumeThatJavaConventionsBlockIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isJavaConventionsBlockSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support java conventions block."
  }
}

fun assumeThatConfigurationCacheIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isConfigurationCacheSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support stable configuration caches."
  }
}

fun assumeThatIsolatedProjectsIsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isIsolatedProjectsSupported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support isolated projects."
  }
}
