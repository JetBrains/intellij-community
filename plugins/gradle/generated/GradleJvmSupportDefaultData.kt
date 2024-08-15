// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gradle.jvmcompat;

import com.intellij.openapi.application.ApplicationInfo
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilityState

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Gradle Compatibility Matrix" configuration instead
 */
internal val DEFAULT_DATA = GradleCompatibilityState(
  supportedJavaVersions = listOf(
    "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22"
  ),
  supportedGradleVersions = listOf(
    "4.5", "4.5.1", "4.6", "4.7", "4.8", "4.9", "4.10", "4.10.3",
    "5.0", "5.1", "5.2", "5.3", "5.3.1", "5.4", "5.4.1", "5.5", "5.5.1", "5.6", "5.6.2",
    "6.0", "6.0.1", "6.1", "6.2", "6.3", "6.4", "6.5", "6.6", "6.7", "6.8", "6.8.3", "6.9",
    "7.0", "7.1", "7.2", "7.3", "7.4", "7.5", "7.5.1", "7.6",
    "8.0", "8.1", "8.2", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10"
  ),
  compatibility = listOf(
    VersionMapping(java = "6-8", gradle = "INF-5.0"),
    VersionMapping(java = "8-9", gradle = "INF-INF"),
    VersionMapping(java = "9-10", gradle = "4.3-INF"),
    VersionMapping(java = "10-11", gradle = "4.7-INF"),
    VersionMapping(java = "11-12", gradle = "5.0-INF"),
    VersionMapping(java = "12-13", gradle = "5.4-INF"),
    VersionMapping(java = "13-14", gradle = "6.0-INF"),
    VersionMapping(java = "14-15", gradle = "6.3-INF"),
    VersionMapping(java = "15-16", gradle = "6.7-INF"),
    VersionMapping(java = "16-17", gradle = "7.0-INF"),
    VersionMapping(java = "17-18", gradle = "7.2-INF"),
    VersionMapping(java = "18-19", gradle = "7.5-INF"),
    VersionMapping(java = "19-20", gradle = "7.6-INF"),
    VersionMapping(java = "20-21", gradle = "8.3-INF"),
    VersionMapping(java = "21-22", gradle = "8.5-INF"),
    VersionMapping(java = "22-23", gradle = "8.8-INF")
  )
);