// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ComponentModuleRegistrationChecker")

package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.AtomicClearableLazyValue
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.jps.model.serialization.PathMacroUtil

class ComponentModuleRegistrationChecker(private val moduleToModuleSet: AtomicClearableLazyValue<MutableMap<String, PluginXmlDomInspection.PluginModuleSet>>,
                                         private val ignoredClasses: MutableList<String>,
                                         private val annotationHolder: DomElementAnnotationHolder) {

  fun checkProperModule(extensionPoint: ExtensionPoint) {
    val interfacePsiClass = extensionPoint.`interface`.value
    if (shouldCheckExtensionPointClassAttribute(interfacePsiClass) &&
        checkProperXmlFileForClass(extensionPoint, interfacePsiClass)) {
      return
    }
    val beanClassPsiClass = extensionPoint.beanClass.value
    if (shouldCheckExtensionPointClassAttribute(beanClassPsiClass) &&
        checkProperXmlFileForClass(extensionPoint, beanClassPsiClass)) {
      return
    }

    for (withElement in extensionPoint.withElements) {
      if (checkProperXmlFileForClass(extensionPoint, withElement.implements.value)) return
    }

    val shortName = extensionPoint.effectiveQualifiedName.substringAfterLast('.')
    val module = extensionPoint.module
    val project = module!!.project

    val psiSearchHelper = PsiSearchHelper.getInstance(project)
    val scope = GlobalSearchScope.projectScope(project)
    if (psiSearchHelper.isCheapEnoughToSearch(shortName, scope, null, null) == PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
      var extensionPointClass: PsiClass? = null
      psiSearchHelper.processElementsWithWord(
        { element, _ ->
          extensionPointClass = getExtensionPointClass(element)
          extensionPointClass == null
        },
        scope,
        shortName,
        UsageSearchContext.IN_STRINGS,
        true,
        false)
      extensionPointClass?.let { checkProperXmlFileForClass(extensionPoint, it) }
    }
  }

  private fun getExtensionPointClass(element: PsiElement): PsiClass? {
    val epName = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return null
    val psiClass = (epName.type as? PsiClassType)?.resolve() ?: return null
    if (psiClass.qualifiedName == "com.intellij.openapi.extensions.ExtensionPointName") {
      return epName.containingClass
    }
    return null
  }

  fun checkProperXmlFileForExtension(element: Extension) {
    if (!element.xmlTag.getAttributeValue("language").isNullOrEmpty()) {
      val beanClass = element.extensionPoint?.beanClass?.value
      if (beanClass != null && InheritanceUtil.isInheritor(beanClass, "com.intellij.lang.LanguageExtensionPoint")) {
        return
      }
    }

    if (!element.xmlTag.getAttributeValue("filetype").isNullOrEmpty()) {
      val beanClass = element.extensionPoint?.beanClass?.value
      if (beanClass != null && InheritanceUtil.isInheritor(beanClass, "com.intellij.openapi.fileTypes.FileTypeExtensionPoint")) {
        return
      }
    }

    for (attributeDescription in element.genericInfo.attributeChildrenDescriptions) {
      val attributeName = attributeDescription.name
      if (attributeName == "forClass") continue

      if (attributeName == "serviceInterface" && element.xmlTag.getAttributeValue("overrides") == "true") continue

      val attributeValue = attributeDescription.getDomAttributeValue(element)
      if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue

      if (attributeValue.converter is PluginPsiClassConverter) {
        val psiClass = attributeValue.value as PsiClass? ?: continue
        if (checkProperXmlFileForClass(element, psiClass)) return
      }
    }


    for (childDescription in element.genericInfo.fixedChildrenDescriptions) {
      val domElement = childDescription.getValues(element).firstOrNull() ?: continue
      val tag = domElement.xmlTag ?: continue
      val project = tag.project
      val psiClass = JavaPsiFacade.getInstance(project).findClass(tag.value.text, GlobalSearchScope.projectScope(project))
      if (psiClass != null && checkProperXmlFileForClass(element, psiClass)) return
    }
  }

  private fun shouldCheckExtensionPointClassAttribute(psiClass: PsiClass?): Boolean {
    psiClass?.fields?.forEach { field ->
      if (TypeUtils.typeEquals(ExtensionPointName::class.java.canonicalName, field.type)) return true
    }
    return false
  }

  fun checkProperXmlFileForClass(element: DomElement, psiClass: PsiClass?): Boolean {
    if (psiClass == null) return false
    if (ignoredClasses.contains(psiClass.qualifiedName)) return false

    val definingModule = psiClass.let { ModuleUtilCore.findModuleForPsiElement(it) } ?: return false

    val elementModule = element.module
    if (elementModule == null || definingModule == elementModule) return false

    val definingPlugin = moduleToModuleSet.value[definingModule.name]
    val elementPlugin = moduleToModuleSet.value[elementModule.name]
    if (definingPlugin != null && definingPlugin === elementPlugin) return false

    var pluginXmlModule = definingModule
    var modulePluginXmlFile = findModulePluginXmlFile(pluginXmlModule)
    if (modulePluginXmlFile == null) {
      val implModule = findMatchingImplModule(pluginXmlModule)
      if (implModule != null) {
        pluginXmlModule = implModule
        modulePluginXmlFile = findModulePluginXmlFile(implModule)
      }
    }
    val fix = if (modulePluginXmlFile != null) MoveRegistrationQuickFix(pluginXmlModule, modulePluginXmlFile.name) else null
    annotationHolder.createProblem(element, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   "Element should be registered in '${definingModule.name}' module where its class '${psiClass.qualifiedName}' is defined", null,
                                   fix)
    return true
  }

  fun isIdeaPlatformModule(module: Module?): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return true
    }
    if (module == null || !PsiUtil.isIdeaProject(module.project)) {
      return false
    }
    val contentRoots = ModuleRootManager.getInstance(module).contentRoots
    if (contentRoots.isEmpty()) return false
    var parent: VirtualFile? = contentRoots[0].parent
    while (parent != null) {
      if (parent.name == "plugins") {
        return false
      }
      if (parent.findChild(PathMacroUtil.DIRECTORY_STORE_NAME) != null) {
        return true
      }
      parent = parent.parent
    }
    return true
  }

  private fun findMatchingImplModule(module: Module): Module? {
    return ModuleManager.getInstance(module.project).findModuleByName(module.name + ".impl")
  }

  private fun findModulePluginXmlFile(module: Module): XmlFile? {
    for (sourceRoot in ModuleRootManager.getInstance(module).getSourceRoots(false)) {
      val metaInf = sourceRoot.findChild("META-INF")
      if (metaInf != null && metaInf.isDirectory) {
        for (file in metaInf.children) {
          if (file.name.endsWith("Plugin.xml")) {
            val psiFile = PsiManager.getInstance(module.project).findFile(file)
            if (psiFile is XmlFile) {
              return psiFile
            }
          }
        }
      }
    }
    return null
  }

  inner class MoveRegistrationQuickFix(private val myTargetModule: Module,
                                       private val myTargetFileName: String) : LocalQuickFix {

    @Nls
    override fun getName(): String = "Move registration to " + myTargetFileName

    @Nls
    override fun getFamilyName(): String = "Move registration to correct module"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val tag = PsiTreeUtil.getParentOfType(descriptor.psiElement, XmlTag::class.java, false) ?: return
      val parentTag = tag.parentTag ?: return

      val targetFile = findModulePluginXmlFile(myTargetModule) ?: return
      val rootTag = targetFile.rootTag ?: return
      val subTags = rootTag.findSubTags(tag.parentTag!!.name)
      val newParentTag = subTags.firstOrNull()
                         ?: rootTag.addSubTag(rootTag.createChildTag(parentTag.localName, "", null, false), false)
                           .apply {
                             for (attribute in parentTag.attributes) {
                               setAttribute(attribute.name, attribute.value)
                             }
                           }

      val anchor = newParentTag.subTags.lastOrNull { it.name == tag.name }
      val newTag = anchor?.let { newParentTag.addAfter(tag, anchor) } ?: newParentTag.addSubTag(tag, false)
      tag.delete()
      (newTag as? Navigatable)?.navigate(true)
    }
  }
}