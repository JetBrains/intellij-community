// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
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
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class ContentModuleVisibilityInspection : DevKitPluginXmlInspectionBase() {

  private val rootPluginNames by lazy { IntelliJPlatformProduct.entries.mapTo(HashSet()) { "${it.platformPrefix}Plugin.xml" } }

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
            getInternalVisibilityProblemMessage(
              dependencyValue, dependencyIncludingPlugin, dependencyNamespace,
              currentXmlFile, currentModuleIncludingPlugin, currentModuleNamespace
            )
          )
          return // report only one problem at once
        }
      }
    }
  }

  private fun getInternalVisibilityProblemMessage(
    dependencyValue: GenericAttributeValue<IdeaPlugin?>,
    dependencyIncludingPlugin: IdeaPlugin,
    dependencyNamespace: String?,
    currentXmlFile: XmlFile,
    currentModuleIncludingPlugin: IdeaPlugin,
    currentModuleNamespace: String?,
  ): @Nls String? {
    val currentIdeaPlugin = DescriptorUtil.getIdeaPlugin(currentXmlFile)
    return if (currentIdeaPlugin != null && isActualPluginDescriptor(currentIdeaPlugin, currentXmlFile)) {
      when {
        dependencyNamespace == null -> message(
          "inspection.content.module.visibility.internal.declared.in.plugin.dependency.namespace.missing",
          dependencyValue.stringValue, dependencyIncludingPlugin.getUniqueFileName(),
          currentModuleIncludingPlugin.getIdOrUniqueFileName(), currentModuleNamespace
        )
        currentModuleNamespace == null -> message(
          "inspection.content.module.visibility.internal.declared.in.plugin.current.namespace.missing",
          dependencyValue.stringValue, dependencyNamespace, dependencyIncludingPlugin.getUniqueFileName(),
          currentModuleIncludingPlugin.getIdOrUniqueFileName()
        )
        else -> message(
          "inspection.content.module.visibility.internal.declared.in.plugin",
          dependencyValue.stringValue, dependencyNamespace, dependencyIncludingPlugin.getUniqueFileName(),
          currentModuleIncludingPlugin.getIdOrUniqueFileName(), currentModuleNamespace
        )
      }
    }
    else {
      when {
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
  }

  private fun getPluginXmlFilesIncludingFileAsContentModule(xmlFile: XmlFile, scope: GlobalSearchScope): Collection<IdeaPlugin> {
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile)
    if (ideaPlugin != null && isActualPluginDescriptor(ideaPlugin, xmlFile)) {
      return listOf(ideaPlugin)
    }
    val moduleVirtualFile = xmlFile.virtualFile ?: return emptyList()
    val psiManager = xmlFile.manager
    return PluginIdDependenciesIndex.findFilesIncludingContentModule(moduleVirtualFile, scope)
      .mapNotNull { psiManager.findFile(it) as? XmlFile }
      .mapNotNull { DescriptorUtil.getIdeaPlugin(it) }
  }

  private fun getRootPluginDescriptorIncludingFileAsContentModule(xmlFile: XmlFile, scope: GlobalSearchScope): Collection<IdeaPlugin> {
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile)
    if (ideaPlugin != null && isActualPluginDescriptor(ideaPlugin, xmlFile)) {
      return listOf(ideaPlugin)
    }
    val moduleVirtualFile = xmlFile.virtualFile ?: return emptyList()
    val psiManager = xmlFile.manager
    return PluginIdDependenciesIndex.findFilesIncludingContentModule(moduleVirtualFile, scope)
      .mapNotNull { psiManager.findFile(it) as? XmlFile }
      .flatMap { getActualIncludingPlugins(it, scope) }
      .distinct()
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
    val currentModuleIncludingPlugins = getRootPluginDescriptorIncludingFileAsContentModule(currentXmlFile, productionXmlFilesScope)
    val dependencyXmlFile = moduleDependency.xmlElement?.containingFile as? XmlFile ?: return
    val dependencyIncludingPlugins = getRootPluginDescriptorIncludingFileAsContentModule(dependencyXmlFile, productionXmlFilesScope)
    for (currentModuleIncludingPlugin in currentModuleIncludingPlugins) {
      if (dependencyIncludingPlugins.contains(currentModuleIncludingPlugin)) continue // are included in the same plugin
      for (dependencyIncludingPlugin in dependencyIncludingPlugins) {
        if (currentModuleIncludingPlugin != dependencyIncludingPlugin) {
          val dependencyModuleName = getModuleName(dependencyXmlFile)
          val dependencyXmlFilePointer = dependencyXmlFile.createSmartPointer()
          val currentIdeaPlugin = DescriptorUtil.getIdeaPlugin(currentXmlFile)
          val message = if (currentIdeaPlugin != null && isActualPluginDescriptor(currentIdeaPlugin, currentXmlFile)) {
            message("inspection.content.module.visibility.private.accessed.from.plugin",
                    dependencyModuleName, dependencyIncludingPlugin.getIdOrUniqueFileName(),
                    currentModuleIncludingPlugin.getIdOrUniqueFileName())
          }
          else {
            message("inspection.content.module.visibility.private",
              dependencyModuleName, dependencyIncludingPlugin.getIdOrUniqueFileName(),
              getModuleName(currentXmlFile), currentModuleIncludingPlugin.getIdOrUniqueFileName()
            )
          }
          holder.createProblem(
            dependencyValue,
            message,
            ChangeModuleModuleVisibilityFix(dependencyModuleName, ContentModuleVisibility.INTERNAL, dependencyXmlFilePointer),
            ChangeModuleModuleVisibilityFix(dependencyModuleName, ContentModuleVisibility.PUBLIC, dependencyXmlFilePointer)
          )
          return // report only one problem at once
        }
      }
    }
  }

  private fun getProjectProductionXmlFilesScope(project: Project): GlobalSearchScope {
    return GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScopesCore.projectProductionScope(project), XmlFileType.INSTANCE)
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

  private fun isActualPluginDescriptor(ideaPlugin: IdeaPlugin, xmlFile: XmlFile): Boolean {
    return ideaPlugin.pluginId != null || (xmlFile.parent?.name == "META-INF" && isPluginXmlName(xmlFile))
  }

  private fun isPluginXmlName(xmlFile: XmlFile): Boolean {
    val fileName = xmlFile.name
    return fileName == "plugin.xml" || fileName in rootPluginNames
  }

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

  private class ChangeModuleModuleVisibilityFix(
    private val moduleName: String,
    private val visibility: ContentModuleVisibility,
    private val dependencyXmlFilePointer: SmartPsiElementPointer<XmlFile>,
  ) : LocalQuickFix {

    override fun getFamilyName(): @IntentionFamilyName String =
      message("inspection.content.module.visibility.private.fix.change.visibility.family.name")

    override fun getName(): @IntentionName String =
      message("inspection.content.module.visibility.private.fix.change.visibility.to.internal.name", moduleName, visibility.value)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val dependencyXmlFile = dependencyXmlFilePointer.dereference() ?: return
      changeVisibility(dependencyXmlFile)
    }

    private fun changeVisibility(pluginXmlFile: XmlFile) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXmlFile) ?: return
      ideaPlugin.contentModuleVisibility.value = visibility
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      val dependencyXmlFile = dependencyXmlFilePointer.dereference() ?: return IntentionPreviewInfo.EMPTY
      val dependencyXmlFileCopy = dependencyXmlFile.copy() as XmlFile
      changeVisibility(dependencyXmlFileCopy)
      return IntentionPreviewInfo.CustomDiff(
        XmlFileType.INSTANCE,
        dependencyXmlFile.name,
        dependencyXmlFile.text,
        dependencyXmlFileCopy.text,
        true
      )
    }
  }

}
