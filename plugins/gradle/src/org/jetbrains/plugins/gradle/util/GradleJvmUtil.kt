// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmUtil")
@file:ApiStatus.Internal
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.service.execution.nonblockingResolveJdkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.base.BaseProperties
import org.jetbrains.plugins.gradle.properties.base.BasePropertiesFile
import org.jetbrains.plugins.gradle.resolvers.GradleJvmResolver
import java.nio.file.Paths

const val GRADLE_LOCAL_JAVA_HOME = "GRADLE_LOCAL_JAVA_HOME"

const val USE_GRADLE_JAVA_HOME = "#GRADLE_JAVA_HOME"
const val USE_GRADLE_LOCAL_JAVA_HOME = "#GRADLE_LOCAL_JAVA_HOME"

fun SdkLookupProvider.nonblockingResolveGradleJvmInfo(project: Project, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  val projectSdk = ProjectRootManager.getInstance(project).projectSdk
  return nonblockingResolveGradleJvmInfo(project, projectSdk, externalProjectPath, gradleJvm)
}

fun SdkLookupProvider.nonblockingResolveGradleJvmInfo(project: Project, projectSdk: Sdk?, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  if (gradleJvm == null) return getSdkInfo()

  val resolvedSdkInfo = GradleJvmResolver.EP_NAME.extensionList
      .firstOrNull { it.canBeResolved(gradleJvm) }
      ?.getResolvedSdkInfo(project, projectSdk, externalProjectPath, this)
  if (resolvedSdkInfo != null) return resolvedSdkInfo

  return nonblockingResolveJdkInfo(projectSdk, gradleJvm)
}

fun getJavaHome(project: Project, externalProjectPath: String?, propertiesFile: BasePropertiesFile<out BaseProperties>): String? {
  if (externalProjectPath == null) {
    return null
  }
  val properties = propertiesFile.getProperties(project, Paths.get(externalProjectPath))
  val javaHomeProperty = properties.javaHomeProperty ?: return null
  return javaHomeProperty.value
}