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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.ProjectScope
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
    val xmlFilesScope = getXmlFilesScope(currentXmlFile.project)
    val currentModuleIncludingFiles = getInclusionContextsForContentModuleOrPluginXmlFile(currentXmlFile, xmlFilesScope)
    val dependencyXmlFile = moduleDependency.xmlElement?.containingFile as? XmlFile ?: return
    val dependencyIncludingFiles = getInclusionContextsForContentModuleOrPluginXmlFile(dependencyXmlFile, xmlFilesScope)

    for (currentModuleInclusionContext in currentModuleIncludingFiles) {
      for (dependencyInclusionContext in dependencyIncludingFiles) {
        if (currentModuleInclusionContext.rootPlugin == dependencyInclusionContext.rootPlugin) continue
        val currentModuleNamespace = currentModuleInclusionContext.registrationPlace.namespace
        val dependencyNamespace = dependencyInclusionContext.registrationPlace.namespace
        if (currentModuleNamespace != dependencyNamespace) {
          val currentModuleVendor = currentModuleInclusionContext.rootPlugin.actualVendor
          val currentModuleRegistrationFile = currentModuleInclusionContext.registrationPlace.xmlElement?.containingFile as? XmlFile
          val fixes =
            if (currentModuleNamespace == null && dependencyNamespace != null && currentModuleRegistrationFile != null &&
                currentModuleVendor != null && currentModuleVendor == dependencyInclusionContext.rootPlugin.actualVendor) {
              arrayOf(SetNamespaceFix(dependencyNamespace, currentModuleInclusionContext.registrationPlace.getIdOrUniqueFileName(), currentModuleRegistrationFile.createSmartPointer()))
            }
            else {
              emptyArray()
            }
          holder.createProblem(
            dependencyValue,
            getInternalVisibilityProblemMessage(
              dependencyValue, dependencyInclusionContext.registrationPlace, dependencyNamespace,
              currentXmlFile, currentModuleInclusionContext.registrationPlace, currentModuleNamespace
            ),
            *fixes
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

  private val IdeaPlugin.pluginIdOrPlainFileName: String?
    get() = pluginId ?: xmlElement?.containingFile?.name

  private fun getInclusionContextsForContentModuleOrPluginXmlFile(xmlFile: XmlFile, scope: GlobalSearchScope): Collection<ContentModuleInclusionContext> {
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile)
    if (ideaPlugin != null && isActualPluginDescriptor(ideaPlugin, xmlFile)) {
      return listOf(ContentModuleInclusionContext(ideaPlugin, ideaPlugin))
    }
    val moduleVirtualFile = xmlFile.virtualFile ?: return emptyList()
    val psiManager = xmlFile.manager
    return PluginIdDependenciesIndex.findFilesIncludingContentModule(moduleVirtualFile, scope)
      .mapToXmlFileAndIdeaPlugin(psiManager)
      .withoutLibraryDuplicates(xmlFile.project)
      .flatMap { (xmlFile, ideaPlugin) -> getRootIncludingPlugins(xmlFile, ideaPlugin, registrationPlace = ideaPlugin, scope) }
      .distinct()
      .sortedWith(compareBy<ContentModuleInclusionContext> { it.rootPlugin.pluginIdOrPlainFileName }.thenBy { it.registrationPlace.pluginIdOrPlainFileName })
  }

  private fun Collection<VirtualFile>.mapToXmlFileAndIdeaPlugin(psiManager: PsiManager): List<Pair<XmlFile, IdeaPlugin>> {
    return mapNotNull {
      val xmlFile = psiManager.findFile(it) as? XmlFile ?: return@mapNotNull null
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile) ?: return@mapNotNull null
      xmlFile to ideaPlugin
    }
  }

  private fun List<Pair<XmlFile, IdeaPlugin>>.withoutLibraryDuplicates(project: Project): List<Pair<XmlFile, IdeaPlugin>> {
    val productionScope = GlobalSearchScopesCore.projectProductionScope(project)
    return groupBy { it.second.pluginIdOrPlainFileName }
      .flatMap { (_, files) ->
        when {
          files.size == 1 -> files
          else -> {
            val productionFiles = files.filter { productionScope.contains(it.first.virtualFile) }
            return if (productionFiles.size < files.size && productionFiles.isNotEmpty()) {
              productionFiles
            } else {
              files
            }
          }
        }
      }
  }

  private data class ContentModuleInclusionContext(
    /** Element of the root `plugin.xml` (or `<idea.platform.prefix>Plugin.xml`) which includes the content module. */
    val rootPlugin: IdeaPlugin,
    /**
     * The XML file where the content module is actually registered.
     */
    val registrationPlace: IdeaPlugin,
  )

  private val IdeaPlugin.namespace: String?
    get() {
      // all <content> must have the same namespace, so take it from the first:
      return this.content.firstOrNull()?.namespace?.value
    }

  private fun IdeaPlugin.getUniqueFileName(): String {
    return UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(xmlElement!!.project, xmlElement!!.containingFile.virtualFile)
  }

  private val IdeaPlugin.actualVendor: String?
    get() {
      val explicitValue = this.vendor.stringValue
      if (explicitValue != null) return explicitValue
      val xmlFileName = xmlElement?.containingFile?.name
      //it would be more reliable to collect data from all included XML files to find the proper vendor, but this heuristic should work ok
      return if (xmlFileName in rootPluginNames) "JetBrains" else null
    }

  private fun checkPrivateVisibility(
    dependencyValue: GenericAttributeValue<IdeaPlugin?>,
    moduleDependency: IdeaPlugin,
    holder: DomElementAnnotationHolder,
  ) {
    val currentXmlFile = dependencyValue.xmlElement?.containingFile as? XmlFile ?: return
    val project = currentXmlFile.project
    val xmlFilesScope = getXmlFilesScope(project)
    val currentModuleInclusionContexts = getInclusionContextsForContentModuleOrPluginXmlFile(currentXmlFile, xmlFilesScope)
    val dependencyXmlFile = moduleDependency.xmlElement?.containingFile as? XmlFile ?: return
    val dependencyInclusionContexts = getInclusionContextsForContentModuleOrPluginXmlFile(dependencyXmlFile, xmlFilesScope)
    for (currentModuleInclusionContext in currentModuleInclusionContexts) {
      val currentModuleIncludingPlugin = currentModuleInclusionContext.rootPlugin
      if (dependencyInclusionContexts.any { it.rootPlugin == currentModuleIncludingPlugin }) continue // are included in the same plugin
      for (dependencyInclusionContext in dependencyInclusionContexts) {
        val dependencyIncludingPlugin = dependencyInclusionContext.rootPlugin
        if (currentModuleIncludingPlugin != dependencyInclusionContext) {
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
          val fixes = buildList {
            if (currentModuleIncludingPlugin.actualVendor == dependencyIncludingPlugin.actualVendor) {
              add(ChangeModuleModuleVisibilityFix(dependencyModuleName, ContentModuleVisibility.INTERNAL, dependencyXmlFilePointer))
            }
            add(ChangeModuleModuleVisibilityFix(dependencyModuleName, ContentModuleVisibility.PUBLIC, dependencyXmlFilePointer))
          }
          holder.createProblem(
            dependencyValue,
            message,
            *fixes.toTypedArray(),
          )
          return // report only one problem at once
        }
      }
    }
  }

  private fun getXmlFilesScope(project: Project): GlobalSearchScope {
    val productionScope = GlobalSearchScopesCore.projectProductionScope(project)
    val librariesScope = ProjectScope.getLibrariesScope(project)
    return GlobalSearchScope.getScopeRestrictedByFileTypes(
      productionScope.union(librariesScope),
      XmlFileType.INSTANCE
    )
  }

  /**
   * If [xmlFile]:
   * - has ID or is `META-INF/plugin.xml`, then return it, as it is an actual plugin
   * - is included via `<xi:include>`, find including plugins (recursively).
   */
  private fun getRootIncludingPlugins(
    xmlFile: XmlFile,
    currentDescriptor: IdeaPlugin,
    registrationPlace: IdeaPlugin,
    scope: GlobalSearchScope,
    visited: MutableSet<XmlFile> = mutableSetOf(),
  ): Collection<ContentModuleInclusionContext> {
    if (!visited.add(xmlFile)) return emptyList() // prevent inclusion cycles
    if (isActualPluginDescriptor(currentDescriptor, xmlFile)) {
      return listOf(ContentModuleInclusionContext(currentDescriptor, registrationPlace))
    }
    return ReferencesSearch.search(xmlFile, scope)
      .filtering { isXiIncluded(it) }
      .findAll()
      .flatMapTo(ArrayList()) { reference ->
        val referencedFile = reference.element.containingFile as? XmlFile ?: return@flatMapTo emptyList()
        val referencedDescriptor = DescriptorUtil.getIdeaPlugin(referencedFile) ?: return@flatMapTo emptyList()
        getRootIncludingPlugins(referencedFile, currentDescriptor = referencedDescriptor, registrationPlace, scope, visited)
      }
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

  private class SetNamespaceFix(
    private val namespace: String,
    private val declaringPluginId: String,
    private val declaringXmlFilePointer: SmartPsiElementPointer<XmlFile>,
  ) : LocalQuickFix {
    override fun getFamilyName(): @IntentionFamilyName String =
      message("inspection.content.module.visibility.internal.fix.set.namespace.family.name")

    override fun getName(): @IntentionName String =
      message("inspection.content.module.visibility.internal.fix.set.namespace.to", declaringPluginId, namespace)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val declaringXmlFile = declaringXmlFilePointer.dereference() ?: return
      setNamespace(declaringXmlFile)
    }

    private fun setNamespace(declaringXmlFile: XmlFile) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(declaringXmlFile) ?: return
      if (ideaPlugin.content.isNotEmpty()) {
        ideaPlugin.content.forEach { it.namespace.stringValue = namespace }
      }
      else {
        ideaPlugin.addContent().namespace.stringValue = namespace
      }
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      val declaringXmlFile = declaringXmlFilePointer.dereference() ?: return IntentionPreviewInfo.EMPTY
      val declaringXmlFileCopy = declaringXmlFile.copy() as XmlFile
      setNamespace(declaringXmlFileCopy)
      return IntentionPreviewInfo.CustomDiff(
        XmlFileType.INSTANCE,
        declaringXmlFile.name,
        declaringXmlFile.text,
        declaringXmlFileCopy.text,
        true
      )
    }
  }
}
