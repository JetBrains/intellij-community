// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.BACKEND_PLATFORM_MODULE_BASE_NAME
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.FRONTEND_PLATFORM_MODULE_BASE_NAME
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeDescriptorDependencyAnalyzer
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.recognizeExplicitDependencyKind

internal class MissingFrontendOrBackendRuntimeDependencyInspection : DevKitPluginXmlInspectionBase() {

  private val moduleNameSuffixToRequiredRuntimeDependency = listOf(
    ".frontend" to FRONTEND_PLATFORM_MODULE_BASE_NAME,
    ".backend" to BACKEND_PLATFORM_MODULE_BASE_NAME,
  )

  private val coreModuleNames = moduleNameSuffixToRequiredRuntimeDependency.map { it.second }

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    val inspectedFile = holder.fileElement.file
    return super.isAllowed(holder)
           && SplitModeQodanaInspectionScopeLimiter.getInstance(inspectedFile.project).shouldInspectFileInQodanaMode(inspectedFile)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is IdeaPlugin) return
    if (!isAllowed(holder)) return
    val containingFileName = element.xmlElement?.containingFile?.name ?: return
    if (containingFileName == "plugin.xml") return // do not check in main modules
    if (!containingFileName.startsWith("intellij.")) return // check only intellij modules

    val currentModuleName = containingFileName.removeSuffix(".xml")
    if (currentModuleName in coreModuleNames) return

    for ((moduleNameSuffix, requiredRuntimeDependency) in moduleNameSuffixToRequiredRuntimeDependency) {
      if (currentModuleName.endsWith(moduleNameSuffix)) {
        val dependencies = element.dependencies
        if (!SplitModeDescriptorDependencyAnalyzer.hasTransitiveDependency(element, requiredRuntimeDependency)) {
          val requiredModuleKind = recognizeExplicitDependencyKind(requiredRuntimeDependency)
                                   ?: error("Unsupported split-mode runtime dependency: $requiredRuntimeDependency")
          val reportedElement = if (dependencies.exists()) dependencies else element
          val reportedXmlElement = reportedElement.xmlElement ?: return
          val currentXmlFile = holder.fileElement.file
          if (SplitModeInspectionExclusionsService.getInstance(currentXmlFile.project).isExcluded(reportedXmlElement,
                                                                                                  MISSING_RUNTIME_DEPENDENCY_SHORT_NAME)) {
            return
          }
          val regularFixes = arrayOf(
            SplitModeDependencyQuickFixes.createAddExplicitDependencyFix(
              currentModuleName,
              requiredModuleKind,
            )
          )
          val suppressionFix = SplitModeInspectionExclusionsService.getInstance(currentXmlFile.project).createSuppressionFixIfApplicable(
            reportedXmlElement,
            MISSING_RUNTIME_DEPENDENCY_SHORT_NAME,
          )
          val fixes = if (suppressionFix != null) regularFixes + suppressionFix else regularFixes
          holder.createProblem(
            reportedElement,
            message(
              "inspection.remote.dev.missing.runtime.dependency.message",
              currentModuleName, moduleNameSuffix, requiredRuntimeDependency
            ),
            *fixes
          )
        }
        return // only one module name suffix can be matched, so don't check more
      }
    }
  }

}
