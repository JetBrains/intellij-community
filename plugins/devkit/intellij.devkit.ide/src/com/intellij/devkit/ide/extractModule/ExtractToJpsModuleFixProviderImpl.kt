// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.ide.extractModule

import com.intellij.codeInspection.LocalQuickFix
import org.jetbrains.idea.devkit.inspections.extractModule.ExtractToJpsModuleFixProvider

internal class ExtractToJpsModuleFixProviderImpl : ExtractToJpsModuleFixProvider {
  override fun extractContentModule(configFileName: String): LocalQuickFix =
    ExtractContentModuleFix(configFileName)

  override fun convertOptionalDependsToContentModule(configFileName: String, optionalDependencyModuleName: String): LocalQuickFix =
    ConvertOptionalDependsToContentModuleFix(configFileName, optionalDependencyModuleName)

  override fun extractContentModuleDescriptorToJpsModule(contentModuleName: String): LocalQuickFix =
    ExtractContentModuleDescriptorToJpsModuleFix(contentModuleName)
}
