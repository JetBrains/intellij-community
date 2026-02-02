// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ComponentModuleRegistrationChecker")

package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter
import org.jetbrains.jps.model.serialization.PathMacroUtil

internal class ComponentModuleRegistrationChecker(
  private val moduleToModuleSet: SynchronizedClearableLazy<MutableMap<String, PluginXmlRegistrationCheckInspection.PluginModuleSet>>,
  private val ignoredClasses: MutableList<String>,
  private val annotationHolder: DomElementAnnotationHolder,
) {

  fun checkProperModule(extensionPoint: ExtensionPoint) {
    val effectiveEpClass = extensionPoint.effectiveClass
    if (shouldCheckExtensionPointClassAttribute(effectiveEpClass) &&
        checkProperXmlFileForClass(extensionPoint, effectiveEpClass)) {
      return
    }

    val shortName = extensionPoint.effectiveQualifiedName.substringAfterLast('.')
    val module = extensionPoint.module
    val project = module!!.project

    val psiSearchHelper = PsiSearchHelper.getInstance(project)
    val scope = GlobalSearchScope.projectScope(project)
    if (psiSearchHelper.isCheapEnoughToSearch(shortName, scope, null) == PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
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

      if (attributeName == "serviceInterface") continue

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

  @ApiStatus.Experimental
  fun checkProperXmlFileForClassesIncludingDependency(element: DomElement) {
    val xmlElement = element.xmlElement ?: return
    for (psiElement in xmlElement.children) {
      val text = when (psiElement) {
                   is XmlTag -> psiElement.value.text
                   is XmlAttribute -> psiElement.value
                   else -> continue
                 } ?: continue
      val project = psiElement.project
      val psiClass = JavaPsiFacade.getInstance(project).findClass(text, GlobalSearchScope.projectScope(project))
      checkProperXmlFileForClassesIncludingDependency(element, psiClass)
    }
  }

  private fun findModuleXmlFile(module: Module): XmlFile? {
    for (sourceRoot in ModuleRootManager.getInstance(module).getSourceRoots(false)) {
      for (file in sourceRoot.children) {
        if (file.name == module.name + ".xml") {
          val psiFile = PsiManager.getInstance(module.project).findFile(file)
          if (psiFile is XmlFile) {
            return psiFile
          }
        }
      }
    }
    return null
  }

  private fun checkProperXmlFileForClassesIncludingDependency(element: DomElement, psiClass: PsiClass?) {
    if (psiClass == null) return
    val definingModule = psiClass.let { ModuleUtilCore.findModuleForPsiElement(it) } ?: return

    val elementModule = element.module
    if (elementModule == null || definingModule == elementModule) return

    val elementXml = findModuleXmlFile(elementModule) ?: findModulePluginXmlFile(elementModule)

    val declaredDependencies = mutableSetOf<String>()
    elementXml?.rootTag
      ?.findSubTags("dependencies")
      ?.flatMap { it.subTags.toList() }
      ?.forEach { depTag ->
        when (depTag.name) {
          "plugin" -> depTag.getAttributeValue("id")?.takeIf { it.isNotBlank() }?.let(declaredDependencies::add)
          "module" -> depTag.getAttributeValue("name")?.takeIf { it.isNotBlank() }?.let(declaredDependencies::add)
        }
      }
    if (definingModule.name.startsWith("intellij.platform.")) return
    val isCoveredByDependencies = declaredDependencies.contains(definingModule.name)
    if (isCoveredByDependencies) return

    val definingPlugin = moduleToModuleSet.value[definingModule.name]
    val elementPlugin = moduleToModuleSet.value[elementModule.name]
    if (definingPlugin != null && definingPlugin === elementPlugin) return

    annotationHolder.createProblem(element, ProblemHighlightType.ERROR,
                                   DevKitBundle.message("inspections.plugin.xml.ComponentModuleRegistrationChecker.element.registered.wrong.module",
                                                        definingModule.name,
                                                        psiClass.qualifiedName), null)
  }


  fun checkProperXmlFileForClass(element: DomElement, psiClass: PsiClass?): Boolean {
    if (psiClass == null) return false
    if (ignoredClasses.contains(psiClass.qualifiedName)) return false
    if (psiClass.hasAnnotation(InternalIgnoreDependencyViolation::class.java.canonicalName)) return false

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
    annotationHolder.createProblem(element, ProblemHighlightType.WARNING,
                                   DevKitBundle.message("inspections.plugin.xml.ComponentModuleRegistrationChecker.element.registered.wrong.module",
                                                        definingModule.name,
                                                        psiClass.qualifiedName), null,
                                   *LocalQuickFix.notNullElements(fix))
    return true
  }

  fun isIdeaPlatformModule(module: Module?): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return true
    }
    if (module == null || !IntelliJProjectUtil.isIntelliJPlatformProject(module.project)) {
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

  inner class MoveRegistrationQuickFix(
    private val myTargetModule: Module,
    private val myTargetFileName: String,
  ) : LocalQuickFix {

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      return IntentionPreviewInfo.EMPTY
    }

    @Nls
    override fun getName(): String =
      DevKitBundle.message("inspections.plugin.xml.ComponentModuleRegistrationChecker.fix.move.registration.name", myTargetFileName)

    @Nls
    override fun getFamilyName(): String =
      DevKitBundle.message("inspections.plugin.xml.ComponentModuleRegistrationChecker.fix.move.registration.family.name")

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
