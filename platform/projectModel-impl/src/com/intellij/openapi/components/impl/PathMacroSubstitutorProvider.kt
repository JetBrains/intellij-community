// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

private val PROVIDER_EP = ExtensionPointName<PathMacroSubstitutorProvider>("com.intellij.pathMacroSubstitutorProvider")

@ApiStatus.Internal
fun getProjectPathMacroSubstitutor(project: Project, configFilePath: String?): PathMacroSubstitutor {
  return configFilePath?.let {
    PROVIDER_EP.extensionList.firstNotNullOfOrNull {
      it.getProjectPathMacroSubstitutor(project, configFilePath)
    }
  } ?: PathMacroManager.getInstance(project)
}

@ApiStatus.Internal
interface PathMacroSubstitutorProvider {

  fun getProjectPathMacroSubstitutor(project: Project, configFilePath: String): PathMacroSubstitutor?
}