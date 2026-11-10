// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.extractModule

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * Creates the quick fixes that extract plugin descriptor parts to separate JPS modules.
 * The fixes need `intellij.java.ui`, so the implementation is an application service registered by
 * the optional `intellij.devkit.ide` content module; [getInstance] returns `null` when that module is not loaded.
 */
@ApiStatus.Internal
interface ExtractToJpsModuleFixProvider {
  fun extractContentModule(configFileName: @NlsSafe String): LocalQuickFix

  fun convertOptionalDependsToContentModule(configFileName: @NlsSafe String, optionalDependencyModuleName: @NlsSafe String): LocalQuickFix

  fun extractContentModuleDescriptorToJpsModule(contentModuleName: @NlsSafe String): LocalQuickFix

  companion object {
    fun getInstance(): ExtractToJpsModuleFixProvider? = serviceOrNull()
  }
}
