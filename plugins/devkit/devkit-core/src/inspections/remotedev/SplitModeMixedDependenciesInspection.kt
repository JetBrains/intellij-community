// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildNonNativePluginMessage
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildMixedModuleDependenciesMessage
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver

internal class SplitModeMixedDependenciesInspection : DevKitPluginXmlInspectionBase() {

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    return super.isAllowed(holder) && SplitModeInspectionUtil.isAllowedForSplitModeInspection(holder.fileElement.file)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is IdeaPlugin) return
    if (!isAllowed(holder)) return

    val module = element.module ?: return
    val currentXmlFile = holder.fileElement.file
    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, currentXmlFile)
    if (SplitModeInspectionUtil.shouldReportSinglePluginLevelError(currentXmlFile, moduleAnalysis)) {
      holder.createProblem(
        element,
        ProblemHighlightType.GENERIC_ERROR,
        buildNonNativePluginMessage(moduleAnalysis.resolvedModuleKind),
        null,
      )
      return
    }
    if (moduleAnalysis.resolvedModuleKind.kind != SplitModeApiRestrictionsService.ModuleKind.MIXED) return

    val mixedDependenciesMessage = buildMixedModuleDependenciesMessage(moduleAnalysis.resolvedModuleKind.reasoning)
    holder.createProblem(
      element,
      ProblemHighlightType.GENERIC_ERROR,
      mixedDependenciesMessage,
      null,
      *SplitModeDependencyQuickFixes.createMixedModuleFixes(module, element)
    )
  }
}
