// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion


abstract class GradleJvmSupportMatricesTestCase : LightIdeaTestCase() {

  fun isSupported(gradleVersion: String, javaVersion: Int) =
    isSupported(GradleVersion.version(gradleVersion), JavaVersion.compose(javaVersion))

  fun suggestGradleVersion(javaVersion: Int): String? =
    suggestGradleVersion(JavaVersion.compose(javaVersion))?.version

  fun suggestJavaVersion(gradleVersion: String): Int? =
    suggestJavaVersion(GradleVersion.version(gradleVersion))?.feature

  fun suggestOldestCompatibleGradleVersion(javaVersion: Int): String? =
    suggestOldestCompatibleGradleVersion(JavaVersion.compose(javaVersion))?.version

  fun suggestOldestCompatibleJavaVersion(gradleVersion: String): Int? =
    suggestOldestCompatibleJavaVersion(GradleVersion.version(gradleVersion))?.feature
}