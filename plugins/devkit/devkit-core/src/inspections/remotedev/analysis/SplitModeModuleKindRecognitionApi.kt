// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class RecognizedSplitModeModuleKind(
  val moduleName: String,
  val kind: SplitModeApiRestrictionsService.ModuleKind,
  val reasoning: String,
)

@ApiStatus.Internal
fun recognizeSplitModeModuleKind(descriptorFile: PsiFile): RecognizedSplitModeModuleKind? {
  val xmlFile = descriptorFile as? XmlFile ?: return null
  val module = ModuleUtilCore.findModuleForPsiElement(descriptorFile) ?: return null
  val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, xmlFile)
  return RecognizedSplitModeModuleKind(
    moduleName = module.name,
    kind = moduleAnalysis.resolvedModuleKind.kind,
    reasoning = moduleAnalysis.resolvedModuleKind.reasoning,
  )
}
