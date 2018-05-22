// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ComponentModuleRegistrationChecker")

package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
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

fun checkProperModule(extensionPoint: ExtensionPoint, holder: DomElementAnnotationHolder, ignoreClassList: List<String>) {
  val interfacePsiClass = extensionPoint.`interface`.value
  if (shouldCheckExtensionPointClassAttribute(interfacePsiClass) &&
      checkProperXmlFileForClass(extensionPoint, holder, interfacePsiClass, ignoreClassList)) {
    return
  }
  val beanClassPsiClass = extensionPoint.beanClass.value
  if (shouldCheckExtensionPointClassAttribute(beanClassPsiClass) &&
      checkProperXmlFileForClass(extensionPoint, holder, beanClassPsiClass, ignoreClassList)) {
    return
  }

  for (withElement in extensionPoint.withElements) {
    if (checkProperXmlFileForClass(extensionPoint, holder, withElement.implements.value, ignoreClassList)) return
  }

  val shortName = extensionPoint.effectiveQualifiedName.substringAfterLast('.')
  val module = extensionPoint.module
  val project = module!!.project

  val psiSearchHelper = PsiSearchHelper.getInstance(project)
  val scope = GlobalSearchScope.projectScope(project)
  if (psiSearchHelper.isCheapEnoughToSearch(shortName, scope, null, null) == PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
    var epRegistration: Module? = null
    psiSearchHelper.processElementsWithWord(
      { element, _ ->
        epRegistration = getRegisteringModule(element)
        epRegistration == null
      },
      scope,
      shortName,
      UsageSearchContext.IN_STRINGS,
      true,
      false)
    epRegistration?.let { checkProperXmlFileForDefinition(extensionPoint, holder, it) }
  }
}

private fun getRegisteringModule(element: PsiElement): Module? {
  val epName = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return null
  val psiClass = (epName.type as? PsiClassType)?.resolve() ?: return null
  if (psiClass.qualifiedName == "com.intellij.openapi.extensions.ExtensionPointName") {
    return ModuleUtilCore.findModuleForPsiElement(epName)
  }
  return null
}

fun checkProperXmlFileForExtension(element: Extension,
                                   holder: DomElementAnnotationHolder,
                                   ignoreClassList: List<String>) {
  for (attributeDescription in element.genericInfo.attributeChildrenDescriptions) {
    val attributeName = attributeDescription.name
    if (attributeName == "interfaceClass" || attributeName == "serviceInterface" || attributeName == "forClass") continue

    val attributeValue = attributeDescription.getDomAttributeValue(element)
    if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue

    if (attributeValue.converter is PluginPsiClassConverter) {
      val psiClass = attributeValue.value as PsiClass? ?: continue
      if (checkProperXmlFileForClass(element, holder, psiClass, ignoreClassList)) return
    }
  }


  for (childDescription in element.genericInfo.fixedChildrenDescriptions) {
    val domElement = childDescription.getValues(element).firstOrNull() ?: continue
    val text = domElement.xmlTag?.value?.text ?: continue
    val project = domElement.xmlTag.project
    val psiClass = JavaPsiFacade.getInstance(project).findClass(text, GlobalSearchScope.projectScope(project))
    if (psiClass != null && checkProperXmlFileForClass(element, holder, psiClass, ignoreClassList)) return
  }
}

private fun shouldCheckExtensionPointClassAttribute(psiClass: PsiClass?): Boolean {
  psiClass?.fields?.forEach { field ->
    if (TypeUtils.typeEquals(ExtensionPointName::class.java.canonicalName, field.type)) return true
  }
  return false
}

fun checkProperXmlFileForClass(element: DomElement,
                               holder: DomElementAnnotationHolder,
                               psiClass: PsiClass?,
                               ignoreClassList : List<String>): Boolean {
  if (ignoreClassList.contains(psiClass?.qualifiedName)) return false
  val definingModule = psiClass?.let { ModuleUtilCore.findModuleForPsiElement(it) } ?: return false
  return checkProperXmlFileForDefinition(element, holder, definingModule)
}

private fun checkProperXmlFileForDefinition(element: DomElement,
                                            holder: DomElementAnnotationHolder,
                                            definingModule: Module): Boolean {
  var definingModule = definingModule
  var modulePluginXmlFile = findModulePluginXmlFile(definingModule)
  if (modulePluginXmlFile == null) {
    val implModule = findMatchingImplModule(definingModule)
    if (implModule != null) {
      definingModule = implModule
      modulePluginXmlFile = findModulePluginXmlFile(implModule)
    }
  }
  if (modulePluginXmlFile != null && element.module !== definingModule) {
    holder.createProblem(element, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         "Element should be registered in ${modulePluginXmlFile.name}", null,
                         MoveRegistrationQuickFix(definingModule, modulePluginXmlFile.name))
    return true
  }
  return false
}

fun isIdeaPlatformModule(module: Module?): Boolean {
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
  for (sourceRoot in ModuleRootManager.getInstance(module).sourceRoots) {
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


class MoveRegistrationQuickFix(private val myTargetModule: Module,
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
