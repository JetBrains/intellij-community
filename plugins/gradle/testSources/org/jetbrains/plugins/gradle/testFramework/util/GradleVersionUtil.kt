// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import org.gradle.util.GradleVersion


fun GradleVersion.isGradleAtLeast(version: String): Boolean =
  baseVersion >= GradleVersion.version(version)

fun GradleVersion.isGradleOlderThan(version: String): Boolean =
  baseVersion < GradleVersion.version(version)