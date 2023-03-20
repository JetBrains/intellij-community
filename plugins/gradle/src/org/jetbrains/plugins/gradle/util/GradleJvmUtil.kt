// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleJvmUtil")
@file:ApiStatus.Internal
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.service.execution.createJdkInfo
import com.intellij.openapi.externalSystem.service.execution.nonblockingResolveJdkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.GRADLE_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.properties.LOCAL_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.properties.LocalPropertiesFile
import org.jetbrains.plugins.gradle.properties.base.BaseProperties
import org.jetbrains.plugins.gradle.properties.base.BasePropertiesFile
import java.nio.file.Paths

const val USE_GRADLE_JAVA_HOME = "#GRADLE_JAVA_HOME"
const val USE_LOCAL_PROPERTIES_JAVA_HOME = "#LOCAL_PROPERTIES_JDK"

fun SdkLookupProvider.nonblockingResolveGradleJvmInfo(project: Project, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  val projectSdk = ProjectRootManager.getInstance(project).projectSdk
  return nonblockingResolveGradleJvmInfo(project, projectSdk, externalProjectPath, gradleJvm)
}

fun SdkLookupProvider.nonblockingResolveGradleJvmInfo(project: Project, projectSdk: Sdk?, externalProjectPath: String?, gradleJvm: String?): SdkInfo {
  return when (gradleJvm) {
    USE_GRADLE_JAVA_HOME -> createJdkInfo(GRADLE_JAVA_HOME_PROPERTY, getJavaHome(project, externalProjectPath, GradlePropertiesFile))
    USE_LOCAL_PROPERTIES_JAVA_HOME -> createJdkInfo(LOCAL_JAVA_HOME_PROPERTY, getJavaHome(project, externalProjectPath, LocalPropertiesFile))
    else -> nonblockingResolveJdkInfo(projectSdk, gradleJvm)
  }
}

fun getJavaHome(project: Project, externalProjectPath: String?, propertiesFile: BasePropertiesFile<out BaseProperties>): String? {
  if (externalProjectPath == null) {
    return null
  }
  val properties = propertiesFile.getProperties(project, Paths.get(externalProjectPath))
  val javaHomeProperty = properties.javaHomeProperty ?: return null
  return javaHomeProperty.value
}