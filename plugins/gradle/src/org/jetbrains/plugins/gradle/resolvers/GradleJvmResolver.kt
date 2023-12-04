// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.resolvers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point to resolve the gradleJvm specified under .gradle/idea.xml allowing custom macros but also
 * special cases that are not directly linked with the gradleJvm used
 */
@ApiStatus.Experimental
abstract class GradleJvmResolver {

  companion object {
    val EP_NAME = ExtensionPointName.create<GradleJvmResolver>("org.jetbrains.plugins.gradle.gradleJvmResolver")
  }

  abstract fun canBeResolved(gradleJvm: String): Boolean

  abstract fun getResolvedSdkInfo(
    project: Project,
    projectSdk: Sdk?,
    externalProjectPath: String?,
    sdkLookupProvider: SdkLookupProvider
  ): SdkInfo

  protected fun createSdkInfo(name: String, homePath: String?): SdkInfo {
    if (homePath == null) return SdkInfo.Undefined
    val type = ExternalSystemJdkUtil.getJavaSdkType()
    val versionString = type.getVersionString(homePath)
    return SdkInfo.Resolved(name, versionString, homePath)
  }
}