// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildImplicitModuleKindMessage
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeAnalysisFlags
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter

internal class SplitModeImplicitModuleKindInspection : DevKitPluginXmlInspectionBase() {

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    return super.isAllowed(holder)
           && SplitModeInspectionUtil.isAllowedForSplitModeInspection(holder.fileElement.file)
           && SplitModeQodanaInspectionScopeLimiter.getInstance(holder.fileElement.file.project)
             .shouldInspectFileInQodanaMode(holder.fileElement.file)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is IdeaPlugin) return
    if (!isAllowed(holder)) return
    if (!SplitModeAnalysisFlags.isReportImplicitModuleKindEnabled()) return

    val module = element.module ?: return
    val currentXmlFile = holder.fileElement.file
    val xmlElement = element.xmlElement ?: return
    val exclusionsService = SplitModeInspectionExclusionsService.getInstance(currentXmlFile.project)
    if (exclusionsService.isExcluded(xmlElement, SPLIT_MODE_IMPLICIT_MODULE_KIND_SHORT_NAME)) {
      return
    }

    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, currentXmlFile)
    if (!SplitModeInspectionUtil.isImplicitFrontendOrBackendMainPluginXml(currentXmlFile, moduleAnalysis)) {
      return
    }

    val regularFixes =
      SplitModeDependencyQuickFixes.createAddExplicitDependenciesFixes(module, element, moduleAnalysis.resolvedModuleKind.kind)
    val suppressionFix = exclusionsService.createSuppressionFixIfApplicable(
      xmlElement,
      SPLIT_MODE_IMPLICIT_MODULE_KIND_SHORT_NAME,
    )
    val quickFixes = if (suppressionFix != null) regularFixes + suppressionFix else regularFixes
    holder.createProblem(
      element,
      ProblemHighlightType.WEAK_WARNING,
      buildImplicitModuleKindMessage(moduleAnalysis.resolvedModuleKind),
      null,
      *quickFixes,
    )
  }
}
