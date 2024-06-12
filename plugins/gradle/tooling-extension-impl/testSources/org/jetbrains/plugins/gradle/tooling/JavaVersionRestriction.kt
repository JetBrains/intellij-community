// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion

fun interface JavaVersionRestriction {

  fun isRestricted(gradleVersion: GradleVersion, source: JavaVersion): Boolean

  companion object {
    @JvmField
    val NO = JavaVersionRestriction { _, _ -> false }
  }
}