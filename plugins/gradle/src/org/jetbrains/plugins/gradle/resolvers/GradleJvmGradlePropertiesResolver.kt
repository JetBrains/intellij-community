// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import org.jetbrains.plugins.gradle.properties.GRADLE_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.util.USE_GRADLE_JAVA_HOME
import org.jetbrains.plugins.gradle.util.getJavaHome

/**
 * A [GradleJvmResolver] implementation to resolve the JDK for the [USE_GRADLE_JAVA_HOME] gradleJvm macro
 */
class GradleJvmGradlePropertiesResolver : GradleJvmResolver() {

  override fun canBeResolved(gradleJvm: String) = gradleJvm == USE_GRADLE_JAVA_HOME

  override fun getResolvedSdkInfo(
    project: Project,
    projectSdk: Sdk?,
    externalProjectPath: String?,
    sdkLookupProvider: SdkLookupProvider
  ) = createSdkInfo(
    name = GRADLE_JAVA_HOME_PROPERTY,
    homePath = getJavaHome(project, externalProjectPath, GradlePropertiesFile)
  )
}