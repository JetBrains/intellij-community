// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.util.JavaVersionMatcher.isVersionMatch

fun interface JavaVersionRestriction {

  fun isRestricted(gradleVersion: GradleVersion, source: JavaVersion): Boolean

  companion object {
    @JvmField
    val NO = JavaVersionRestriction { _, _ -> false }

    /**
     * @param targetVersionNotation the java version restriction in string form.
     * The notation variants can be found in the [org.jetbrains.plugins.gradle.tooling.annotation.TargetJavaVersion] documentation.
     */
    @JvmStatic
    fun javaRestrictionOf(targetVersionNotation: String): JavaVersionRestriction {
      return JavaVersionRestriction { _, source -> !isVersionMatch(source, targetVersionNotation) }
    }
  }
}