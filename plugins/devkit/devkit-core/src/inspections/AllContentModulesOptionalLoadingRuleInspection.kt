// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.dom.IdeaPlugin

internal class AllContentModulesOptionalLoadingRuleInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is IdeaPlugin) return
    if (!isAllowed(holder)) return
    if (!element.hasRealPluginId()) return

    val allModules = element.content.flatMap { it.moduleEntry }
    if (allModules.isEmpty()) return

    if (hasClassesRegisteredDirectly(element)) return

    val hasNonOptionalLoading = allModules.any { hasNonOptionalLoadingRule(it) }
    if (!hasNonOptionalLoading) {
      holder.createProblem(
        element.content.first(),
        DevKitBundle.message("inspection.all.content.modules.default.loading.rule.message")
      )
    }
  }

  private fun hasClassesRegisteredDirectly(plugin: IdeaPlugin): Boolean {
    if (plugin.extensionPoints.any { it.extensionPoints.isNotEmpty() }) return true
    if (plugin.extensions.any { it.xmlTag.subTags.isNotEmpty() }) return true
    if (plugin.applicationListeners.any { it.listeners.isNotEmpty() }) return true
    if (plugin.projectListeners.any { it.listeners.isNotEmpty() }) return true
    if (plugin.actions.any { it.actions.isNotEmpty() || it.groups.isNotEmpty() || it.references.isNotEmpty() }) return true
    return false
  }

  private fun hasNonOptionalLoadingRule(module: ModuleDescriptor): Boolean {
    val loading = module.loading.value
    if (loading == ModuleLoadingRule.REQUIRED || loading == ModuleLoadingRule.EMBEDDED) return true
    if (DomUtil.hasXml(module.requiredIfAvailable)) return true
    return false
  }
}
