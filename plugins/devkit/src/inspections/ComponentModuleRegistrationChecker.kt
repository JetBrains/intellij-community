/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("ComponentModuleRegistrationChecker")

package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
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
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter
import org.jetbrains.idea.devkit.util.PsiUtil

fun checkProperModule(extensionPoint: ExtensionPoint, holder: DomElementAnnotationHolder) {
  if (checkProperXmlFileForClass(extensionPoint, holder, extensionPoint.`interface`.value)) {
    return
  }
  if (checkProperXmlFileForClass(extensionPoint, holder, extensionPoint.beanClass.value)) {
    return
  }
  for (withElement in extensionPoint.withElements) {
    if (checkProperXmlFileForClass(extensionPoint, holder, withElement.implements.value)) return
  }

  val shortName = extensionPoint.effectiveQualifiedName.substringAfterLast('.')
  val module = extensionPoint.module
  val project = module!!.project

  val psiSearchHelper = PsiSearchHelper.SERVICE.getInstance(project)
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

fun checkProperXmlFileForExtension(element: Extension, holder: DomElementAnnotationHolder) {
  for (attributeDescription in element.genericInfo.attributeChildrenDescriptions) {
    val attributeName = attributeDescription.name
    if (attributeName == "interfaceClass" || attributeName == "serviceInterface" || attributeName == "forClass") continue

    val attributeValue = attributeDescription.getDomAttributeValue(element)
    if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue

    if (attributeValue.converter is PluginPsiClassConverter) {
      val psiClass = attributeValue.value as PsiClass? ?: continue
      if (checkProperXmlFileForClass(element, holder, psiClass)) return
    }
  }


  for (childDescription in element.genericInfo.fixedChildrenDescriptions) {
    val domElement = childDescription.getValues(element).firstOrNull() ?: continue
    val text = domElement.xmlTag?.value?.text ?: continue
    val project = domElement.xmlTag.project
    val psiClass = JavaPsiFacade.getInstance(project).findClass(text, GlobalSearchScope.projectScope(project))
    if (psiClass != null && checkProperXmlFileForClass(element, holder, psiClass)) return
  }
}

fun checkProperXmlFileForClass(element: DomElement, holder: DomElementAnnotationHolder, psiClass: PsiClass?): Boolean {
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
    if (parent.findChild(".idea") != null) {
      return true
    }
    parent = parent.parent
  }
  return true
}

private fun findMatchingImplModule(module: Module): Module? {
  if (module.name == "openapi") {
    return ModuleManager.getInstance(module.project).findModuleByName("java-impl")
  }

  if (module.name.endsWith("-api")) {
    val implName = module.name.substring(0, module.name.length - 4) + "-impl"
    return ModuleManager.getInstance(module.project).findModuleByName(implName)
  }
  return null
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

    val newTag = newParentTag.addSubTag(tag, false)
    tag.delete()
    (newTag as? Navigatable)?.navigate(true)
  }
}
