// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.model.Pointer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.createSmartPointer
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

internal class ModuleNotRegisteredAsPluginContentInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : XmlElementVisitor() {
      override fun visitXmlFile(file: XmlFile) {
        if (DescriptorUtil.isPluginModuleFile(file) && isNotReferencedAsContentModule(file)) {
          val moduleName = getModuleName(file)
          holder.registerProblem(
            file,
            DevKitBundle.message("inspection.module.not.registered.as.plugin.content.message", moduleName),
            *fixIfPluginXmlFound(file, moduleName)
          )
        }
      }
    }
  }

  private fun getModuleName(xmlFile: XmlFile): String {
    return xmlFile.virtualFile.nameWithoutExtension
  }

  private fun isNotReferencedAsContentModule(xmlFile: XmlFile): Boolean {
    val moduleVirtualFile = xmlFile.virtualFile ?: return false
    return PluginIdDependenciesIndex.findFilesIncludingContentModule(xmlFile.project, moduleVirtualFile).isEmpty()
  }

  private fun fixIfPluginXmlFound(file: XmlFile, moduleName: String): Array<out LocalQuickFix> {
    val (pluginXmlFile, pluginId) = findParentModuleWithPluginXml(file) ?: return LocalQuickFix.EMPTY_ARRAY
    return arrayOf(AddAsContentModuleFix(pluginXmlFile.createSmartPointer(), pluginId, moduleName))
  }

  private fun findParentModuleWithPluginXml(xmlFile: XmlFile): Pair<XmlFile, String>? {
    val project = xmlFile.project
    val checkedModules: MutableSet<Module> = mutableSetOf()
    var currentDir = xmlFile.containingFile.virtualFile.parent
    while (currentDir != null) {
      val currentAndSiblingDirsModules = (currentDir.parent?.children ?: arrayOf(currentDir))
        .mapNotNull { ModuleUtil.findModuleForFile(it, project) }
        .filterNot { checkedModules.contains(it) }
        .distinct()
      for (module in currentAndSiblingDirsModules) {
        if (!checkedModules.add(module)) continue
        val rootManager = ModuleRootManager.getInstance(module)
        val resourcesRoots = rootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES)
        for (resourcesRoot in resourcesRoots) {
          val metaInf = resourcesRoot.findChild("META-INF") ?: continue
          val foundPluginXml = metaInf.findChild("plugin.xml")?.let { xmlFile.manager.findFile(it) as? XmlFile } ?: continue
          val ideaPlugin = DescriptorUtil.getIdeaPlugin(foundPluginXml) ?: continue
          val pluginIdOrPlaceholder = ideaPlugin.pluginId ?: continue // we expect a correct plugin with ID
          return Pair(foundPluginXml, pluginIdOrPlaceholder)
        }
      }
      currentDir = currentDir.parent
    }
    return null
  }

  private class AddAsContentModuleFix(
    private val pluginXmlFilePointer: Pointer<XmlFile>,
    private val pluginId: String,
    private val addedModuleName: String,
  ) : LocalQuickFix {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val pluginXmlFile = pluginXmlFilePointer.dereference() ?: return
      includeModuleInPluginXml(pluginXmlFile)
    }

    private fun includeModuleInPluginXml(pluginXmlFile: XmlFile) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXmlFile) ?: return
      ideaPlugin.content.addModuleEntry().name.stringValue = addedModuleName
    }

    override fun getFamilyName(): String {
      return DevKitBundle.message("inspection.module.not.registered.as.plugin.content.fix.add", pluginId)
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      val originalPluginXmlFile = pluginXmlFilePointer.dereference() ?: return IntentionPreviewInfo.EMPTY
      val pluginXmlFileCopy = originalPluginXmlFile.copy() as XmlFile
      includeModuleInPluginXml(pluginXmlFileCopy)
      return IntentionPreviewInfo.CustomDiff(
        XmlFileType.INSTANCE,
        originalPluginXmlFile.name,
        originalPluginXmlFile.text,
        pluginXmlFileCopy.text,
        true
      )
    }
  }
}
