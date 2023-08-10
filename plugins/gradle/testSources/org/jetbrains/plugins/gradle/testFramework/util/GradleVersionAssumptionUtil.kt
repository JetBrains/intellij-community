// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleAtLeast
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleOlderThan
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJunit5Supported
import org.junit.jupiter.api.Assumptions


fun assumeThatGradleIsAtLeast(gradleVersion: GradleVersion, version: String) {
  assumeThatGradleIsAtLeast(gradleVersion, version) {
    "Test cannot be executed on Gradle versions older than $version."
  }
}

fun assumeThatGradleIsAtLeast(gradleVersion: GradleVersion, version: String, lazyMessage: () -> String) {
  Assumptions.assumeTrue(gradleVersion.isGradleAtLeast(version), lazyMessage)
}

fun assumeThatGradleIsOlderThan(gradleVersion: GradleVersion, version: String) {
  assumeThatGradleIsOlderThan(gradleVersion, version) {
    "Test cannot be executed on Gradle versions newer than $version."
  }
}

fun assumeThatGradleIsOlderThan(gradleVersion: GradleVersion, version: String, lazyMessage: () -> String) {
  Assumptions.assumeTrue(gradleVersion.isGradleOlderThan(version), lazyMessage)
}

fun assumeThatJunit5IsSupported(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isJunit5Supported(gradleVersion)) {
    "Gradle ${gradleVersion.version} doesn't support Junit 5."
  }
}

fun assumeThatKotlinIsSupported(gradleVersion: GradleVersion) {
  assumeThatGradleIsAtLeast(gradleVersion, "5.6.2") {
    "Gradle ${gradleVersion.version} doesn't support Kotlin."
  }
}

fun assumeThatSpockIsSupported(gradleVersion: GradleVersion) {
  assumeThatGradleIsAtLeast(gradleVersion, "5.6") {
    "Gradle ${gradleVersion.version} doesn't support Spock."
  }
}

fun assumeThatTopLevelJavaConventionsIsSupported(gradleVersion: GradleVersion) {
  assumeThatGradleIsOlderThan(gradleVersion, "8.2") {
    "Gradle ${gradleVersion.version} doesn't support top-level java conventions."
  }
}

fun assumeThatJavaConventionsBlockIsSupported(gradleVersion: GradleVersion) {
  assumeThatGradleIsAtLeast(gradleVersion, "7.1") {
    "Gradle ${gradleVersion.version} doesn't support java conventions block."
  }
}
fun assumeThatConfigurationCacheIsSupported(gradleVersion: GradleVersion) {
  assumeThatGradleIsAtLeast(gradleVersion, "8.1") {
    "Gradle ${gradleVersion.version} doesn't stable configuration caches."
  }
}
