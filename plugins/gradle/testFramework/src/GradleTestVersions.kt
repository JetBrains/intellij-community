// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework

object GradleTestVersions {
  /**
   * Note: When adding new versions here, change also lists:
   * - Idea_Tests_BuildToolsTests
   * - IntelliJ TeamCity configuration
   * - [BASE_GRADLE_VERSION]
   * - [org.jetbrains.plugins.gradle.jvmcompat.DEFAULT_DATA]
   * - "gradle.versions.list" file in the resources
   */
  val SUPPORTED_GRADLE_VERSIONS = listOf(
    "4.6", /*"4.7", "4.8.1", "4.9",*/ "4.10.3",
    "5.0", /*"5.1.1", "5.2.1", "5.3.1", "5.4.1", "5.5.1",*/ "5.6.4",
    "6.0.1", /*"6.1.1", "6.2.2", "6.3", "6.4.1", "6.5.1", "6.6.1", "6.7.1", "6.8.3",*/ "6.9.4",
    "7.0.2", /*"7.1.1", "7.2", "7.3.3", "7.4.2", "7.5.1",*/ "7.6.6",
    "8.0.2", /*"8.1.1", "8.2.1", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10.2", "8.11.1", "8.12.1", "8.13",*/ "8.14.6",
    "9.0.0", /*"9.1.0", "9.2.1", "9.3.1", "9.4.1", "9.5.1", "9.6.1", "9.7.1",*/ "9.8.2"
  )

  const val BASE_GRADLE_VERSION = "9.8.2"
}