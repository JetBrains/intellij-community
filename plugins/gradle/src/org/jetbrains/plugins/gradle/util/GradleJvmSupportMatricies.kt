// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion

const val MINIMUM_SUPPORTED_JAVA = 7
const val MAXIMUM_SUPPORTED_JAVA = 16

fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
  val baseVersion = gradleVersion.baseVersion
  val featureVersion = javaVersion.feature
  return when {
    // Gradle 7.2 and java 17 are partially compatible
    // https://github.com/gradle/gradle/issues/16857
    baseVersion >= GradleVersion.version("7.2") -> featureVersion in 8..17
    // https://docs.gradle.org/7.0/release-notes.html#java-16
    baseVersion >= GradleVersion.version("7.0") -> featureVersion in 8..16
    // https://docs.gradle.org/6.7/release-notes.html#java-15
    baseVersion >= GradleVersion.version("6.7") -> featureVersion in 8..15
    // many builds might work with Java 15 but there are some known issues https://github.com/gradle/gradle/issues/13532
    baseVersion >= GradleVersion.version("6.3") -> featureVersion in 8..14
    baseVersion >= GradleVersion.version("6.0") -> featureVersion in 8..13
    baseVersion >= GradleVersion.version("5.4.1") -> featureVersion in 8..12
    baseVersion >= GradleVersion.version("5.0") -> featureVersion in 8..11
    baseVersion >= GradleVersion.version("4.1") -> featureVersion in 7..9
    baseVersion >= GradleVersion.version("4.0") -> featureVersion in 7..8
    else -> featureVersion in 7..8
  }
}

fun suggestGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  val featureVersion = javaVersion.feature
  return when {
    isSupported(GradleVersion.current(), javaVersion) -> GradleVersion.current()
    featureVersion in 8..17 -> GradleVersion.version("7.2")
    // https://docs.gradle.org/5.0/release-notes.html#potential-breaking-changes
    featureVersion == 7 -> GradleVersion.version("4.10.3")
    else -> null
  }
}

fun suggestJavaVersion(gradleVersion: GradleVersion): JavaVersion {
  val baseVersion = gradleVersion.baseVersion
  return when {
    baseVersion >= GradleVersion.version("7.2") -> JavaVersion.compose(17)
    baseVersion >= GradleVersion.version("7.0") -> JavaVersion.compose(16)
    baseVersion >= GradleVersion.version("6.7") -> JavaVersion.compose(15)
    baseVersion >= GradleVersion.version("6.3") -> JavaVersion.compose(14)
    baseVersion >= GradleVersion.version("6.0") -> JavaVersion.compose(13)
    baseVersion >= GradleVersion.version("5.4.1") -> JavaVersion.compose(12)
    baseVersion >= GradleVersion.version("5.0") -> JavaVersion.compose(11)
    baseVersion >= GradleVersion.version("4.1") -> JavaVersion.compose(9)
    else -> JavaVersion.compose(8)
  }
}

fun suggestOldestCompatibleGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  val featureVersion = javaVersion.feature
  return when {
    featureVersion >= 17 -> GradleVersion.version("7.2")
    featureVersion >= 16 -> GradleVersion.version("7.0")
    featureVersion >= 15 -> GradleVersion.version("6.7")
    featureVersion >= 14 -> GradleVersion.version("6.3")
    featureVersion >= 13 -> GradleVersion.version("6.0")
    featureVersion >= 12 -> GradleVersion.version("5.4.1")
    featureVersion >= 11 -> GradleVersion.version("4.8")
    featureVersion >= 7 -> GradleVersion.version("3.0")
    else -> null
  }
}

fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion {
  val baseVersion = gradleVersion.baseVersion
  return when {
    baseVersion >= GradleVersion.version("5.0") -> JavaVersion.compose(8)
    else -> JavaVersion.compose(7)
  }
}
