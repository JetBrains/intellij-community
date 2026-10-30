// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.jvm

import com.intellij.gradle.testFramework.util.JavaVersionMatcher.isVersionMatch
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion

fun interface JavaVersionRestriction {

  fun isRestricted(gradleVersion: GradleVersion, source: JavaVersion): Boolean

  companion object {
    @JvmField
    val NO = JavaVersionRestriction { _, _ -> false }

    @JvmField
    val DEFAULT = compositeOf(listOf(
      GradleToolingApi9VersionRestriction(),
      GradleBrokenJvmSerialisationVersionRestriction()
    ))

    /**
     * @param targetVersionNotation the java version restriction in string form.
     * The notation variants can be found in the [com.intellij.gradle.testFramework.annotations.TargetJavaVersion] documentation.
     */
    @JvmStatic
    fun javaRestrictionOf(targetVersionNotation: String): JavaVersionRestriction {
      return JavaVersionRestriction { _, source -> !isVersionMatch(source, targetVersionNotation) }
    }

    @JvmStatic
    fun compositeOf(restrictions: List<JavaVersionRestriction>): JavaVersionRestriction {
      return JavaVersionRestriction { gradleVersion, source ->
        restrictions.any { it.isRestricted(gradleVersion, source) }
      }
    }
  }
}