// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@JvmDefaultWithCompatibility
interface GradleExecutionAware : ExternalSystemExecutionAware {
  fun getBuildLayoutParameters(project: Project, projectPath: String): BuildLayoutParameters? = null
  fun getDefaultBuildLayoutParameters(project: Project): BuildLayoutParameters? = null
  fun isGradleInstallationHomeDir(project: Project, homePath: String): Boolean = false
}