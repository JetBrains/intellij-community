// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import com.intellij.xml.util.XmlUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.ContentModuleVisibility
import org.jetbrains.idea.devkit.dom.DependencyDescriptor
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class ContentModuleVisibilityInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    val dependencyModule = element as? DependencyDescriptor.ModuleDescriptor ?: return
    val dependencyName = dependencyModule.name
    val dependency = dependencyName.value ?: return
    val dependencyVisibility = dependency.contentModuleVisibility
    when (dependencyVisibility.value ?: ContentModuleVisibility.PRIVATE) {
      ContentModuleVisibility.PUBLIC -> return // can be accessed from anywhere
      ContentModuleVisibility.INTERNAL -> checkInternalVisibility(dependencyName, dependency, holder)
      ContentModuleVisibility.PRIVATE -> checkPrivateVisibility(dependencyName, dependency, holder)
    }
  }

  private fun checkInternalVisibility(
    dependencyValue: GenericAttributeValue<IdeaPlugin?>,
    moduleDependency: IdeaPlugin,
    holder: DomElementAnnotationHolder,
  ) {
    val currentXmlFile = dependencyValue.xmlElement?.containingFile as? XmlFile ?: return
    val productionXmlFilesScope = getProjectProductionXmlFilesScope(currentXmlFile.project)
    val currentModuleIncludingPlugins = getPluginXmlFilesIncludingFileAsContentModule(currentXmlFile, productionXmlFilesScope)
    val dependencyXmlFile = moduleDependency.xmlElement?.containingFile as? XmlFile ?: return
    val dependencyIncludingPlugins = getPluginXmlFilesIncludingFileAsContentModule(dependencyXmlFile, productionXmlFilesScope)

    for (currentModuleIncludingPlugin in currentModuleIncludingPlugins) {
      for (dependencyIncludingPlugin in dependencyIncludingPlugins) {
        if (currentModuleIncludingPlugin == dependencyIncludingPlugin) continue
        val currentModuleNamespace = currentModuleIncludingPlugin.namespace
        val dependencyNamespace = dependencyIncludingPlugin.namespace
        if (currentModuleNamespace != dependencyNamespace) {
          holder.createProblem(
            dependencyValue,
            getInternalVisibilityProblemMessage(dependencyValue, dependencyIncludingPlugin, currentXmlFile, currentModuleIncludingPlugin)
          )
          return // report only one problem at once
        }
      }
    }
  }

  private fun getInternalVisibilityProblemMessage(
    dependencyValue: GenericAttributeValue<IdeaPlugin?>,
    dependencyIncludingPlugin: IdeaPlugin,
    currentXmlFile: XmlFile,
    currentModuleIncludingPlugin: IdeaPlugin,
  ): @Nls String? {
    val dependencyNamespace = dependencyIncludingPlugin.namespace
    val currentModuleNamespace = currentModuleIncludingPlugin.namespace
    return when {
      dependencyNamespace == null -> message(
        "inspection.content.module.visibility.internal.dependency.namespace.missing",
        dependencyValue.stringValue, dependencyIncludingPlugin.getUniqueFileName(),
        getModuleName(currentXmlFile), currentModuleNamespace, currentModuleIncludingPlugin.getUniqueFileName()
      )
      currentModuleNamespace == null -> message(
        "inspection.content.module.visibility.internal.current.namespace.missing",
        dependencyValue.stringValue, dependencyNamespace, dependencyIncludingPlugin.getUniqueFileName(),
        getModuleName(currentXmlFile), currentModuleIncludingPlugin.getUniqueFileName()
      )
      else -> message(
        "inspection.content.module.visibility.internal",
        dependencyValue.stringValue, dependencyNamespace, dependencyIncludingPlugin.getUniqueFileName(),
        getModuleName(currentXmlFile), currentModuleNamespace, currentModuleIncludingPlugin.getUniqueFileName()
      )
    }
  }

  private fun getPluginXmlFilesIncludingFileAsContentModule(xmlFile: XmlFile, scope: GlobalSearchScope): Collection<IdeaPlugin> {
    val moduleVirtualFile = xmlFile.virtualFile ?: return emptyList()
    val psiManager = xmlFile.manager
    return PluginIdDependenciesIndex.findFilesIncludingContentModule(moduleVirtualFile, scope)
      .mapNotNull { psiManager.findFile(it) as? XmlFile }
      .mapNotNull { DescriptorUtil.getIdeaPlugin(it) }
  }

  private val IdeaPlugin.namespace: String?
    get() {
      // all <content> must have the same namespace, so take it from the first:
      return this.content.firstOrNull()?.namespace?.value
    }

  private fun IdeaPlugin.getUniqueFileName(): String {
    return UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(xmlElement!!.project, xmlElement!!.containingFile.virtualFile)
  }

  private fun checkPrivateVisibility(
    dependencyValue: GenericAttributeValue<IdeaPlugin?>,
    moduleDependency: IdeaPlugin,
    holder: DomElementAnnotationHolder,
  ) {
    val currentXmlFile = dependencyValue.xmlElement?.containingFile as? XmlFile ?: return
    val project = currentXmlFile.project
    val productionXmlFilesScope = getProjectProductionXmlFilesScope(project)
    val currentModuleIncludingPlugins = getPluginsIncludingFileAsContentModule(currentXmlFile, productionXmlFilesScope)
    val dependencyXmlFile = moduleDependency.xmlElement?.containingFile as? XmlFile ?: return
    val dependencyIncludingPlugins = getPluginsIncludingFileAsContentModule(dependencyXmlFile, productionXmlFilesScope)
    for (currentModuleIncludingPlugin in currentModuleIncludingPlugins) {
      if (dependencyIncludingPlugins.contains(currentModuleIncludingPlugin)) continue // are included in the same plugin
      for (dependencyIncludingPlugin in dependencyIncludingPlugins) {
        if (currentModuleIncludingPlugin != dependencyIncludingPlugin) {
          holder.createProblem(
            dependencyValue,
            message(
              "inspection.content.module.visibility.private",
              getModuleName(dependencyXmlFile), dependencyIncludingPlugin.getIdOrUniqueFileName(),
              getModuleName(currentXmlFile), currentModuleIncludingPlugin.getIdOrUniqueFileName()
            )
          )
          return // report only one problem at once
        }
      }
    }
  }

  private fun getProjectProductionXmlFilesScope(project: Project): GlobalSearchScope {
    return GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScopesCore.projectProductionScope(project), XmlFileType.INSTANCE)
  }

  private fun getPluginsIncludingFileAsContentModule(xmlFile: XmlFile, scope: GlobalSearchScope): Collection<IdeaPlugin> {
    val moduleVirtualFile = xmlFile.virtualFile ?: return emptyList()
    val psiManager = xmlFile.manager
    return PluginIdDependenciesIndex.findFilesIncludingContentModule(moduleVirtualFile, scope)
      .mapNotNull { psiManager.findFile(it) as? XmlFile }
      .flatMap { getActualIncludingPlugins(it, scope) }
      .distinct()
  }

  /**
   * If [xmlFile]:
   * - has ID or is `META-INF/plugin.xml`, then return it, as it is an actual plugin
   * - is included via `<xi:include>`, find including plugins (recursively).
   */
  private fun getActualIncludingPlugins(
    xmlFile: XmlFile,
    scope: GlobalSearchScope,
    visited: MutableSet<XmlFile> = mutableSetOf(),
  ): Collection<IdeaPlugin> {
    if (!visited.add(xmlFile)) return emptyList() // prevent inclusion cycles
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile)
    if (ideaPlugin != null && isActualPluginDescriptor(ideaPlugin, xmlFile)) {
      return listOf(ideaPlugin)
    }
    return ReferencesSearch.search(xmlFile, scope)
      .filtering { isXiIncluded(it) }
      .findAll()
      .flatMap { getActualIncludingPlugins(it.element.containingFile as XmlFile, scope, visited) }
  }

  private fun isActualPluginDescriptor(ideaPlugin: IdeaPlugin, xmlFile: XmlFile): Boolean =
    ideaPlugin.pluginId != null || (xmlFile.name == "plugin.xml" && xmlFile.parent?.name == "META-INF")

  private fun isXiIncluded(reference: PsiReference): Boolean {
    val xmlTag = reference.element.parentOfType<XmlTag>() ?: return false
    return xmlTag.namespace == XmlUtil.XINCLUDE_URI && xmlTag.localName == "include"
  }

  private fun getModuleName(xmlFile: XmlFile): String {
    return xmlFile.virtualFile.nameWithoutExtension
  }

  private fun IdeaPlugin.getIdOrUniqueFileName(): String {
    return pluginId ?: getUniqueFileName()
  }

}
