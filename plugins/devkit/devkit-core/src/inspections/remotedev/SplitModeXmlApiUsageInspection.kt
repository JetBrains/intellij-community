// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.Extensions
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildModuleKindMismatchMessage
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver.doesApiKindMatchExpectedModuleKind
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class SplitModeXmlApiUsageInspection : DevKitPluginXmlInspectionBase() {

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    return super.isAllowed(holder)
           && SplitModeInspectionUtil.isAllowedForSplitModeInspection(holder.fileElement.file)
           && SplitModeQodanaInspectionScopeLimiter.getInstance(holder.fileElement.file.project).shouldInspectFileInQodanaMode(holder.fileElement.file)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension && element !is Extensions.UnresolvedExtension) return
    if (!isAllowed(holder)) return

    val extensionPointName = getExtensionPointName(element) ?: return
    val module = element.module ?: return
    val currentXmlFile = holder.fileElement.file
    val restrictionsService = SplitModeApiRestrictionsService.getInstance(currentXmlFile.project)
    val expectedModuleKind = restrictionsService.getExtensionPointKind(extensionPointName) ?: return
    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, currentXmlFile)
    if (SplitModeInspectionUtil.shouldReportSinglePluginLevelErrorInsteadOfManyNestedErrors(currentXmlFile, moduleAnalysis)) return
    val actualModuleKind = moduleAnalysis.resolvedModuleKind

    if (doesApiKindMatchExpectedModuleKind(actualModuleKind, expectedModuleKind)) return

    val currentlyOpenedDescriptor = DescriptorUtil.getIdeaPlugin(holder.fileElement.file)
    val hint = restrictionsService.getExtensionPointHint(extensionPointName)
    val xmlElement = element.xmlElement ?: return
    if (SplitModeInspectionExclusionsService.getInstance(currentXmlFile.project).isExcluded(xmlElement, SPLIT_MODE_XML_API_USAGE_SHORT_NAME)) {
      return
    }
    val regularFixes = SplitModeDependencyQuickFixes.createMismatchFixes(module, currentlyOpenedDescriptor, expectedModuleKind)
    val suppressionFix = SplitModeInspectionExclusionsService.getInstance(currentXmlFile.project).createSuppressionFixIfApplicable(
      xmlElement,
      SPLIT_MODE_XML_API_USAGE_SHORT_NAME,
    )
    val fixes = if (suppressionFix != null) regularFixes + suppressionFix else regularFixes
    holder.createProblem(
      element,
      buildModuleKindMismatchMessage(extensionPointName, expectedModuleKind, actualModuleKind, hint),
      *fixes
    )
  }

  private fun getExtensionPointName(element: DomElement): String? {
    if (element is Extension) {
      val qualifiedName = element.extensionPoint?.effectiveQualifiedName
      if (qualifiedName != null) {
        return qualifiedName
      }
    }

    val extensions = element.getParentOfType(Extensions::class.java, true) ?: return null
    return extensions.epPrefix + element.xmlElementName
  }
}
