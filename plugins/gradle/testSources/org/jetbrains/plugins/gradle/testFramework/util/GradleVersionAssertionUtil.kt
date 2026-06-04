// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isConfigurationCacheSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isDependencyResolutionManagementSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGroovy5Supported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isIsolatedProjectsSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJavaConventionsBlockSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJunit5Supported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isKotlinDslScriptsModelImportSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isKotlinSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isSpockSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isTopLevelJavaConventionsSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isVersionCatalogsSupported
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.Companion.OLDEST_NON_DEPRECATED_GRADLE_VERSION_STRING
import org.junit.jupiter.api.Assertions

const val NON_DEPRECATED_BY_IDEA_VERSIONS = "$OLDEST_NON_DEPRECATED_GRADLE_VERSION_STRING+"
const val DEPRECATED_BY_IDEA_VERSIONS = "<$OLDEST_NON_DEPRECATED_GRADLE_VERSION_STRING"
const val JUNIT_5_SUPPORTED_VERSIONS = "4.7+"
const val GROOVY_5_SUPPORTED_VERSIONS = "7.0+"
const val SPOCK_SUPPORTED_VERSIONS = "5.6+"
const val TOP_LEVEL_JAVA_CONVENTIONS_SUPPORTED_VERSIONS = "<8.2"
const val JAVA_CONVENTIONS_BLOCK_SUPPORTED_VERSIONS = "7.1+"
const val CONFIGURATION_CACHE_SUPPORTED_VERSIONS = "8.1+"
const val ISOLATED_PROJECTS_SUPPORTED_VERSIONS = "8.8+"
const val VERSION_CATALOGS_SUPPORTED_VERSIONS = "7.4+"
const val DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS = "6.8+"

const val KOTLIN_SUPPORTED_VERSIONS = "5.6.2+"
const val KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS = "6.0+"
const val KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS = "<9.6.0"
const val KOTLIN_DSL_BASE_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS = "9.2+"
const val KOTLIN_DSL_BASE_SCRIPTS_MODEL_IMPORT_UNSUPPORTED_VERSIONS = "<9.2"

fun assertThatGradleIsAtLeast(gradleVersion: GradleVersion, version: String) {
  assertThatGradleIsAtLeast(gradleVersion, version) {
    """
      Test cannot be executed on Gradle versions older than $version.
      Please, use @TargetVersions("$version+") annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatGradleIsAtLeast(gradleVersion: GradleVersion, version: String, lazyMessage: () -> String) {
  Assertions.assertTrue(GradleVersionUtil.isGradleAtLeast(gradleVersion, version), lazyMessage)
}

fun assertThatGradleIsOlderThan(gradleVersion: GradleVersion, version: String) {
  assertThatGradleIsOlderThan(gradleVersion, version) {
    """
      Test cannot be executed on Gradle versions newer than $version.
      Please, use @TargetVersions("<$version") annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatGradleIsOlderThan(gradleVersion: GradleVersion, version: String, lazyMessage: () -> String) {
  Assertions.assertTrue(GradleVersionUtil.isGradleOlderThan(gradleVersion, version), lazyMessage)
}

fun assertThatJunit5IsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isJunit5Supported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support Junit 5.
      Please, use @TargetVersions(JUNIT_5_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatGroovy5IsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isGroovy5Supported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support Groovy 5.
      Please, use @TargetVersions(GROOVY_5_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatKotlinIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isKotlinSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support Kotlin.
      Please, use @TargetVersions(KOTLIN_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatKotlinDslScriptsModelImportIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isKotlinDslScriptsModelImportSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support KotlinDslScriptsModel import.
      Please, use @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatSpockIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isSpockSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support Spock.
      Please, use @TargetVersions(SPOCK_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatTopLevelJavaConventionsIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isTopLevelJavaConventionsSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support top-level java conventions.
      Please, use @TargetVersions(TOP_LEVEL_JAVA_CONVENTIONS_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatJavaConventionsBlockIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isJavaConventionsBlockSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support java conventions block.
      Please, use @TargetVersions(JAVA_CONVENTIONS_BLOCK_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatConfigurationCacheIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isConfigurationCacheSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support stable configuration caches.
      Please, use @TargetVersions(CONFIGURATION_CACHE_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatIsolatedProjectsIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isIsolatedProjectsSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support isolated projects.
      Please, use @TargetVersions(ISOLATED_PROJECTS_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatVersionCatalogsAreSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isVersionCatalogsSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support version catalogs.
      Please, use @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}

fun assertThatDependencyResolutionManagementIsSupported(gradleVersion: GradleVersion) {
  Assertions.assertTrue(isDependencyResolutionManagementSupported(gradleVersion)) {
    """
      Gradle ${gradleVersion.version} doesn't support DependencyResolutionManagement.
      Please use @TargetVersions(DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS) annotation to ignore this version.
    """.trimIndent()
  }
}
