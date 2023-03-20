// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmSupportMatrices")
@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.project.Project
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings


/**
 * Checks that Java with [javaVersion] is supported by Gradle with [gradleVersion].
 */
fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
  return GradleJvmSupportMatrix.getInstance().isSupported(gradleVersion, javaVersion)
}

/**
 * Returns sorted list (from min to max) of Gradle version which supported by current Idea.
 */
fun getAllSupportedGradleVersions(): List<GradleVersion> {
  return GradleJvmSupportMatrix.getInstance().getAllSupportedGradleVersions()
}

/**
 * Returns sorted list (from min to max) of Java version which supported by current Idea.
 */
fun getAllSupportedJavaVersions(): List<JavaVersion> {
  return GradleJvmSupportMatrix.getInstance().getAllSupportedJavaVersions()
}

fun getSupportedGradleVersions(javaVersion: JavaVersion): List<GradleVersion> {
  return getAllSupportedGradleVersions().filter { isSupported(it, javaVersion) }
}

fun getSupportedJavaVersions(gradleVersion: GradleVersion): List<JavaVersion> {
  return getAllSupportedJavaVersions().filter { isSupported(gradleVersion, it) }
}

fun suggestLatestGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return getSupportedGradleVersions(javaVersion).lastOrNull()
}

fun suggestLatestJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return getSupportedJavaVersions(gradleVersion).lastOrNull()
}

fun suggestOldestCompatibleGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return getSupportedGradleVersions(javaVersion).firstOrNull()
}

fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return getSupportedJavaVersions(gradleVersion).firstOrNull()
}

/**
 * Suggest preferred Gradle version for specified restrictions.
 * @return null if Gradle version doesn't exist for these restrictions.
 */
fun suggestGradleVersion(configure: SuggestGradleVersionOptions.() -> Unit): GradleVersion? {
  val options = SuggestGradleVersionOptions().apply(configure)
  return suggestGradleVersion(options)
}

fun suggestGradleVersion(options: SuggestGradleVersionOptions): GradleVersion? {
  val versions = ArrayList<GradleVersion>()
  if (options.checkDefaultProjectSettingsVersion) {
    versions.addIfNotNull(getDefaultGradleVersion())
  }
  if (options.checkLinkedProjectSettingsVersion) {
    val project = options.project
    if (project != null) {
      versions.addAll(project.getLinkedGradleVersions())
    }
  }
  versions.add(GradleVersion.current())
  versions.addAll(getAllSupportedGradleVersions().asReversed())
  return versions.find { v -> options.filters.all { it(v) } }
}

private fun Project.getProjectJdkVersion(): JavaVersion? {
  val projectJdk = resolveProjectJdk() ?: return null
  return JavaVersion.tryParse(projectJdk.versionString)
}

private fun getDefaultGradleVersion(): GradleVersion? {
  return GradleDefaultProjectSettings.getInstance().gradleVersion
}

private fun Project.getLinkedGradleVersions(): List<GradleVersion> {
  val settings = GradleSettings.getInstance(this)
  return settings.linkedProjectsSettings
    .mapNotNull { it.resolveGradleVersion() }
    .sorted()
}

class SuggestGradleVersionOptions {

  var project: Project? = null

  var filters: List<(GradleVersion) -> Boolean> = emptyList()

  var checkDefaultProjectSettingsVersion: Boolean = true

  var checkLinkedProjectSettingsVersion: Boolean = true

  fun withFilter(filter: (GradleVersion) -> Boolean) = apply {
    filters += filter
  }

  fun withProject(project: Project?) = apply {
    this.project = project
  }

  fun withJavaVersionFilter(javaVersion: JavaVersion?) = apply {
    if (javaVersion != null) {
      withFilter { isSupported(it, javaVersion) }
    }
  }

  fun withProjectJdkVersionFilter(project: Project?) = apply {
    withJavaVersionFilter(project?.getProjectJdkVersion())
  }

  fun dontCheckDefaultProjectSettingsVersion() = apply {
    checkDefaultProjectSettingsVersion = false
  }

  fun dontCheckLinkedProjectSettingsVersion() = apply {
    checkLinkedProjectSettingsVersion = false
  }
}
