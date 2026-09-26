// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.include.FileIncludeManager
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomManager
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

    if (hasClassesRegisteredDirectly(element, mutableSetOf())) return

    val hasNonOptionalLoading = allModules.any { hasNonOptionalLoadingRule(it) }
    if (!hasNonOptionalLoading) {
      holder.createProblem(
        element.content.first(),
        DevKitBundle.message("inspection.all.content.modules.default.loading.rule.message")
      )
    }
  }

  private fun hasClassesRegisteredDirectly(plugin: IdeaPlugin, processedFiles: MutableSet<VirtualFile>): Boolean {
    val virtualFile = plugin.xmlTag?.containingFile?.virtualFile
    if (virtualFile != null && !processedFiles.add(virtualFile)) return false

    if (plugin.extensionPoints.any { it.extensionPoints.isNotEmpty() }) return true
    if (plugin.extensions.any { it.xmlTag.subTags.isNotEmpty() }) return true
    if (plugin.applicationListeners.any { it.listeners.isNotEmpty() }) return true
    if (plugin.projectListeners.any { it.listeners.isNotEmpty() }) return true
    if (plugin.actions.any { it.actions.isNotEmpty() || it.groups.isNotEmpty() || it.references.isNotEmpty() }) return true
    return hasClassesRegisteredInIncludedFiles(plugin, processedFiles)
  }

  private fun hasClassesRegisteredInIncludedFiles(plugin: IdeaPlugin, processedFiles: MutableSet<VirtualFile>): Boolean {
    val xmlFile = plugin.xmlTag?.containingFile as? XmlFile ?: return false
    val virtualFile = xmlFile.virtualFile ?: return false
    val project = xmlFile.project
    val includedFiles = FileIncludeManager.getManager(project).getIncludedFiles(virtualFile, true, true)
    if (includedFiles.isEmpty()) return false
    val psiManager = PsiManager.getInstance(project)
    return includedFiles.any { includedVirtualFile ->
      val includedXmlFile = psiManager.findFile(includedVirtualFile) as? XmlFile ?: return@any false
      hasRegistrationsInXmlFile(includedXmlFile, processedFiles)
    }
  }

  private fun hasRegistrationsInXmlFile(xmlFile: XmlFile, processedFiles: MutableSet<VirtualFile>): Boolean {
    val ideaPlugin = DomManager.getDomManager(xmlFile.project).getDomElement(xmlFile.rootTag) as? IdeaPlugin ?: return false
    return hasClassesRegisteredDirectly(ideaPlugin, processedFiles)
  }

  private fun hasNonOptionalLoadingRule(module: ModuleDescriptor): Boolean {
    val loading = module.loading.value
    if (loading == ModuleLoadingRule.REQUIRED || loading == ModuleLoadingRule.EMBEDDED) return true
    if (DomUtil.hasXml(module.requiredIfAvailable)) return true
    return false
  }
}
