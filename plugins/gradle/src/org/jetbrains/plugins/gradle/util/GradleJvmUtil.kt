// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmUtil")
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.service.execution.resolveJdkInfo
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.GradleLocalPropertiesFile
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.resolvers.GradleJvmResolver
import java.nio.file.Paths

const val GRADLE_LOCAL_JAVA_HOME = "GRADLE_LOCAL_JAVA_HOME"

const val USE_GRADLE_JAVA_HOME = "#GRADLE_JAVA_HOME"
const val USE_GRADLE_LOCAL_JAVA_HOME = "#GRADLE_LOCAL_JAVA_HOME"

/**
 * The initial naming of the method is wrong, the method has nothing to do with "non-blocking execution".
 * The method could be suspendable without any side-effects.
 * The original intention of the "non-blocking" is to show that the code inside shouldn't download JDK and execute
 * any heavy and long operations inside.
 * The actual resolution of JDK would be performed when a Gradle execution would be started.
 */
@Deprecated("Use resolveGradleJvmInfo instead")
fun SdkLookupProvider.nonblockingResolveGradleJvmInfo(project: Project, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  return runBlockingCancellable {
    resolveGradleJvmInfo(project, externalProjectPath, gradleJvm)
  }
}

suspend fun SdkLookupProvider.resolveGradleJvmInfo(project: Project, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  val projectSdk = ProjectRootManager.getInstance(project).projectSdk
  return resolveGradleJvmInfo(project, projectSdk, externalProjectPath, gradleJvm)
}

/**
 * The initial naming of the method is wrong, the method has nothing to do with "non-blocking execution".
 * The method could be suspendable without any side-effects.
 * The original intention of the "non-blocking" is to show that the code inside shouldn't download JDK and execute
 * any heavy and long operations inside.
 * The actual resolution of JDK would be performed when a Gradle execution would be started.
 */
@Deprecated("Use resolveGradleJvmInfo instead")
fun SdkLookupProvider.nonblockingResolveGradleJvmInfo(project: Project, projectSdk: Sdk?, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  return runBlockingCancellable {
    resolveGradleJvmInfo(project, projectSdk, externalProjectPath, gradleJvm)
  }
}

suspend fun SdkLookupProvider.resolveGradleJvmInfo(project: Project, projectSdk: Sdk?, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  if (gradleJvm == null) return getSdkInfo()

  val resolvedSdkInfo = GradleJvmResolver.EP_NAME.extensionList
    .firstOrNull { it.canBeResolved(gradleJvm) }
    ?.getResolvedSdkInfo(project, projectSdk, externalProjectPath, this)
  if (resolvedSdkInfo != null) return resolvedSdkInfo

  return resolveJdkInfo(project, projectSdk, gradleJvm)
}

fun GradlePropertiesFile.getJavaHome(project: Project, externalProjectPath: String?): String? {
  if (externalProjectPath == null) return null
  val properties = getProperties(project, Paths.get(externalProjectPath))
  return properties.javaHomeProperty?.value
}

fun GradleLocalPropertiesFile.getJavaHome(externalProjectPath: String?): String? {
  if (externalProjectPath == null) return null
  val properties = getProperties(Paths.get(externalProjectPath))
  return properties.javaHomeProperty?.value
}