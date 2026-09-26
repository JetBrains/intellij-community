// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.extractModule

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.idea.devkit.DevKitBundle

internal class ConvertOptionalDependsToContentModuleFix(private val configFileName: @NlsSafe String, private val optionalDependencyModuleName: @NlsSafe String) : LocalQuickFix {
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    project.service<ExtractToJpsModuleService>().convertOptionalDependsToContentModule(descriptor, optionalDependencyModuleName)
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return DevKitBundle.message("inspection.optional.dependency.convert.to.content.module.family.name")
  }

  override fun getName(): @IntentionName String {
    return DevKitBundle.message("inspection.optional.dependency.convert.to.content.module.name", configFileName, optionalDependencyModuleName)
  }

  override fun availableInBatchMode(): Boolean = false

  override fun startInWriteAction(): Boolean = false
}