// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent

/**
 * The extension point allows providing project-wide path macros (contrary to the
 * [com.intellij.openapi.application.PathMacroContributor] which is the application-wide)
 * 
 * If you want your custom project-wide path macro to be available in the JPS then consider implementing separate Path Macro
 * contributor for the JPS too [org.jetbrains.jps.model.serialization.JpsPathMacroContributor]
 *
 * The implementation must be thread-safe
 */
@ApiStatus.Internal
interface ProjectWidePathMacroContributor {
    /**
     * @param projectFilePath See [com.intellij.openapi.project.Project.getProjectFilePath]
     */
    fun getProjectPathMacros(projectFilePath: @SystemIndependent String): Map<String, String>
}

/**
 * @param projectFilePath See [com.intellij.openapi.project.Project.getProjectFilePath]
 */
@ApiStatus.Internal
fun getAllMacros(projectFilePath: @SystemIndependent String): Map<String, String> {
  val result: MutableMap<String, String> = HashMap()
  EP_NAME.forEachExtensionSafe { contributor ->
    result.putAll(contributor.getProjectPathMacros(projectFilePath))
  }
  return result
}

private val EP_NAME: ExtensionPointName<ProjectWidePathMacroContributor> = ExtensionPointName("com.intellij.projectPathMacroContributor")
