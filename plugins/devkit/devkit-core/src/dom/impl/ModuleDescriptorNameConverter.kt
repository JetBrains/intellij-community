// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.util.Processor
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.ElementPresentationManager
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.ResolvingConverter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

private const val SUB_DESCRIPTOR_DELIMITER = "/"
private const val SUB_DESCRIPTOR_FILENAME_DELIMITER = "."
private const val GRADLE_MAIN_MODULE_SUFFIX = ".main"
private val LOOKUP_PRIORITY = Key.create<Double>("LOOKUP_PRIORITY")
private val CONTEXT = Key.create<ModuleContext>("MODULE_CONTEXT")

class ModuleDescriptorNameConverter : ResolvingConverter<IdeaPlugin>() {

  override fun getErrorMessage(s: String?, context: ConvertContext): String? {
    val value = s?.trim() ?: ""
    val (_, descriptorFileName) = getJpsModuleNameAndDescriptorFileName(value)
    return DevKitBundle.message("plugin.xml.convert.module.descriptor.name", descriptorFileName)
  }

  private fun getJpsModuleNameAndDescriptorFileName(moduleName: String): Pair<String, String> {
    return if (isSubDescriptor(moduleName)) {
      getSubDescriptorModuleName(moduleName) to getSubDescriptorFileName(moduleName)
    }
    else {
      moduleName to getDescriptorFileName(moduleName)
    }
  }

  override fun fromString(s: String?, context: ConvertContext): IdeaPlugin? {
    if (s == null || s.isEmpty()) return null
    val moduleManager = ModuleManager.getInstance(context.project)
    val (jpsModuleName, descriptorFileName) = getJpsModuleNameAndDescriptorFileName(s)
    return findDescriptorInModuleSources(jpsModuleName, descriptorFileName, moduleManager)
           ?: findDescriptorFileInGradleSubProjectContent(moduleManager, jpsModuleName, descriptorFileName)
           ?: findDescriptorInModuleLibraries(context, descriptorFileName)
  }

  private fun findDescriptorInModuleSources(
    jpsModuleName: String,
    descriptorFileName: String,
    moduleManager: ModuleManager,
  ): IdeaPlugin? {
    val module = moduleManager.findModuleByName(jpsModuleName)
    if (module != null) {
      val plugin = findDescriptorFileInModuleSources(module, descriptorFileName)
      if (plugin != null) {
        return plugin
      }
    }
    return null
  }

  override fun getPsiElement(resolvedValue: IdeaPlugin?): PsiElement? {
    return resolvedValue?.xmlElement?.containingFile
  }

  override fun handleElementRename(genericValue: GenericDomValue<IdeaPlugin?>?, context: ConvertContext?, newElementName: String) {
    super.handleElementRename(genericValue, context, newElementName.removeSuffix(".xml"))
  }

  override fun toString(plugin: IdeaPlugin?, context: ConvertContext): String? {
    if (plugin == null) return null
    return getDisplayName(plugin)
  }

  override fun createLookupElement(plugin: IdeaPlugin): LookupElement {
    val displayName = getDisplayName(plugin)
    val obj = getPsiElement(plugin)!!
    val builder = LookupElementBuilder.create(obj, displayName)
      .withIcon(ElementPresentationManager.getIconForClass(ContentDescriptor.ModuleDescriptor::class.java))
      .withBoldness(isSubDescriptor(displayName))
      .withTypeText(plugin.getPackage().stringValue)

    val priority = plugin.getUserData(LOOKUP_PRIORITY)
    return if (priority != null) PrioritizedLookupElement.withPriority(builder, priority) else builder
  }

  override fun getVariants(context: ConvertContext): Collection<IdeaPlugin> {
    val currentModule = context.module ?: return emptyList()
    val project = context.project
    val variants = mutableListOf<IdeaPlugin>()

    val dependencies = mutableSetOf<Module>().apply {
      ModuleUtilCore.getDependencies(currentModule, this)
      remove(currentModule)
    }

    for (module in ModuleManager.getInstance(project).modules) {
      val prioritize = module === currentModule || dependencies.contains(module)
      val moduleName = module.name

      processModuleSourceRoots(module) { root ->
        root.children
          .filter { it.extension == "xml" && it.name.startsWith(moduleName) }
          .mapNotNull {
            findIdeaPlugin(root, it.name, project)?.apply {
              if (prioritize) {
                putUserData(LOOKUP_PRIORITY, if (module === currentModule) 200.0 else 100.0)
              }
            }
          }
          .forEach { variants.add(it) }
        true
      }
      // plugin Gradle projects:
      if (moduleName.endsWith(GRADLE_MAIN_MODULE_SUFFIX)) {
        for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
          val pluginModuleName = moduleName.removeSuffix(GRADLE_MAIN_MODULE_SUFFIX)
          val resources = contentRoot.findChild("resources")?.takeIf { it.isDirectory } ?: continue
          resources.children
            .filter { it.extension == "xml" && it.name.startsWith(pluginModuleName) }
            .mapNotNull { findIdeaPlugin(resources, it.name, project)?.apply { putUserData(CONTEXT, ModuleContext.SOURCES_GRADLE) } }
            .forEach { variants.add(it) }
        }
      }
    }
    processModuleLibraryRoots(currentModule) { root ->
      val libraryName = root.nameWithoutExtension
      root.children
        .filter { it.extension == "xml" && it.name.startsWith(libraryName) }
        .mapNotNull { findIdeaPlugin(root, it.name, project)?.apply { putUserData(CONTEXT, ModuleContext.LIBRARY) } }
        .forEach { variants.add(it) }
      true
    }
    return variants
  }

  private fun getDisplayName(plugin: IdeaPlugin): String {
    val context = plugin.getUserData(CONTEXT) ?: ModuleContext.SOURCES
    val descriptorFile = DomUtil.getFile(plugin).virtualFile
    val jpsModuleName = when (context) {
      ModuleContext.SOURCES -> plugin.module!!.name
      ModuleContext.SOURCES_GRADLE -> plugin.module!!.name.removeSuffix(GRADLE_MAIN_MODULE_SUFFIX)
      ModuleContext.LIBRARY -> descriptorFile.parent!!.nameWithoutExtension
    }
    val pluginModuleName = descriptorFile.nameWithoutExtension
    if (jpsModuleName == pluginModuleName) {
      return pluginModuleName
    }
    return jpsModuleName + SUB_DESCRIPTOR_DELIMITER + pluginModuleName.substringAfterLast(SUB_DESCRIPTOR_FILENAME_DELIMITER)
  }

  private fun findDescriptorFileInModuleSources(module: Module, fileName: String): IdeaPlugin? {
    var ideaPlugin: IdeaPlugin? = null
    processModuleSourceRoots(module) {
      ideaPlugin = findIdeaPlugin(it, fileName, module.project)
      ideaPlugin == null // continue if not found
    }
    return ideaPlugin
  }

  private fun findDescriptorFileInGradleSubProjectContent(
    moduleManager: ModuleManager,
    moduleNamePrefix: String,
    fileName: String,
  ): IdeaPlugin? {
    val module = moduleManager.findModuleByName("$moduleNamePrefix$GRADLE_MAIN_MODULE_SUFFIX") ?: return null
    for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
      val candidate = contentRoot.findFileByRelativePath("resources/$fileName")
      val ideaPlugin = findIdeaPlugin(candidate, module.project)
      if (ideaPlugin != null) {
        return ideaPlugin
      }
    }
    return null
  }

  private fun findDescriptorInModuleLibraries(context: ConvertContext, fileName: String): IdeaPlugin? {
    val currentModule = context.module ?: return null
    var ideaPlugin: IdeaPlugin? = null
    processModuleLibraryRoots(currentModule) {
      ideaPlugin = findIdeaPlugin(it, fileName, context.project)
      ideaPlugin == null // continue if not found
    }
    return ideaPlugin
  }

  private fun findIdeaPlugin(root: VirtualFile, fileName: String, project: Project): IdeaPlugin? {
    return findIdeaPlugin(root.findChild(fileName), project)
  }

  private fun findIdeaPlugin(candidate: VirtualFile?, project: Project): IdeaPlugin? {
    candidate ?: return null
    val psiFile = PsiManager.getInstance(project).findFile(candidate)
    if (DescriptorUtil.isPluginXml(psiFile)) {
      return DescriptorUtil.getIdeaPlugin(psiFile as XmlFile)
    }
    return null
  }

  private fun processModuleSourceRoots(module: Module, processor: Processor<VirtualFile>) {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    for (root in moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE)) {
      if (!processor.process(root)) return
    }
    for (root in moduleRootManager.getSourceRoots(JavaSourceRootType.SOURCE)) {
      if (!processor.process(root)) return
    }
  }

  private fun processModuleLibraryRoots(module: Module, processor: Processor<VirtualFile>) {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    for (entry in moduleRootManager.orderEntries) {
      if (entry is LibraryOrderEntry) {
        val library = entry.library ?: continue
        val roots = library.getFiles(OrderRootType.CLASSES)
        for (root in roots) {
          if (!processor.process(root)) return
        }
      }
    }
  }

  private fun getDescriptorFileName(fileName: String): String {
    return "$fileName.xml"
  }

  private fun isSubDescriptor(value: String): Boolean {
    return value.contains(SUB_DESCRIPTOR_DELIMITER)
  }

  private fun getSubDescriptorModuleName(value: String): String {
    return value.substringBefore(SUB_DESCRIPTOR_DELIMITER)
  }

  private fun getSubDescriptorFileName(value: String): String {
    val moduleName = getSubDescriptorModuleName(value)
    val fileName = value.substringAfter(SUB_DESCRIPTOR_DELIMITER)
    return getDescriptorFileName(moduleName + SUB_DESCRIPTOR_FILENAME_DELIMITER + fileName)
  }

}

private enum class ModuleContext {
  SOURCES, SOURCES_GRADLE, LIBRARY;
}
