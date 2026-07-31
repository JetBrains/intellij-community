// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Dependency
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.processing.collectRegisteredClasses
import org.jetbrains.idea.devkit.inspections.extractModule.ConvertOptionalDependsToContentModuleFix
import org.jetbrains.idea.devkit.inspections.extractModule.ExtractContentModuleFix
import org.jetbrains.jps.model.java.JavaResourceRootType

internal class OptionalDependencyViaDependsTagInspection : DevKitPluginXmlInspectionBase() {
  override fun checkDomElement(
    element: DomElement,
    holder: DomElementAnnotationHolder,
    helper: DomHighlightingHelper,
  ) {
    val dependsTag = element as? Dependency ?: return
    if (dependsTag.optional.value == true) {
      val configFile = dependsTag.resolvedConfigFile
      val fixes =
        if (configFile != null && IntelliJProjectUtil.isIntelliJPlatformProject(element.xmlElement?.project))
          arrayOf(createQuickFix(configFile, dependsTag))
        else
          emptyArray()
      holder.createProblem(dependsTag.optional, DevKitBundle.message("inspection.optional.dependency.declared.by.depends.tag.message"), *fixes)
    }
  }

  private fun createQuickFix(configFile: XmlFile, dependsTag: Dependency): LocalQuickFix {
    val configFileElement = DomManager.getDomManager(configFile.project).getFileElement(configFile, IdeaPlugin::class.java)?.rootElement
    if (configFileElement != null) {
      val registeredClasses = collectRegisteredClasses(configFileElement)
      val pluginXmlModule = dependsTag.xmlElement?.let { ModuleUtilCore.findModuleForPsiElement(it) }
      val optionalDependencyModule = registeredClasses.mapNotNullTo(HashSet()) { ModuleUtilCore.findModuleForPsiElement(it) }.singleOrNull()
      if (optionalDependencyModule != null && pluginXmlModule != null && optionalDependencyModule != pluginXmlModule && !isContentModule(optionalDependencyModule)) {
        return ConvertOptionalDependsToContentModuleFix(configFile.name, optionalDependencyModule.name)
      }
    }
    return ExtractContentModuleFix(configFile.name)
  }

  private fun isContentModule(module: Module): Boolean {
    return ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE).any {
      it.findChild("${module.name}.xml") != null
    }
  }
}
