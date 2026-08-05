// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildMixedModuleDependenciesMessage
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter

internal class SplitModeMixedDependenciesInspection : DevKitPluginXmlInspectionBase() {

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    return super.isAllowed(holder)
           && SplitModeInspectionUtil.isAllowedForSplitModeInspection(holder.fileElement.file)
           && SplitModeQodanaInspectionScopeLimiter.getInstance(holder.fileElement.file.project)
             .shouldInspectFileInQodanaMode(holder.fileElement.file)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is IdeaPlugin) return
    if (!isAllowed(holder)) return

    val module = element.module ?: return
    val currentXmlFile = holder.fileElement.file
    if (element.xmlElement == null) return

    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, currentXmlFile)
    if (moduleAnalysis.resolvedModuleKind.kind == SplitModeApiRestrictionsService.ModuleKind.MIXED) {
      val regularFixes = SplitModeDependencyQuickFixes.createMixedModuleFixes(module, element)
      val suppressionFixes = SplitModeInspectionExclusionsService.getInstance(currentXmlFile.project).createCommonSuppressionQuickFixes()
      val quickFixes = regularFixes + suppressionFixes
      val mixedDependenciesMessage = buildMixedModuleDependenciesMessage(moduleAnalysis.resolvedModuleKind.reasoning)
      holder.createProblem(
        element,
        ProblemHighlightType.GENERIC_ERROR,
        mixedDependenciesMessage,
        null,
        *quickFixes
      )
      return
    }
  }
}
