// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.util.JvmClassUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint

fun locateExtensionsByClass(project: Project, clazz: JvmClass): ExtensionLocator {
  return ExtensionByClassLocator(project, clazz)
}

fun locateExtensionsByPsiClass(psiClass: PsiClass): ExtensionLocator {
  return ExtensionByPsiClassLocator(psiClass)
}

fun locateExtensionsByExtensionPoint(extensionPoint: ExtensionPoint): ExtensionLocator {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, null)
}

fun locateExtensionsByExtensionPointAndId(extensionPoint: ExtensionPoint, extensionId: String): ExtensionLocator {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, extensionId)
}

private fun processExtensionDeclarations(name: String, project: Project, strictMatch: Boolean, callback: (Extension, XmlTag) -> Boolean) {
  val scope = PluginRelatedLocatorsUtils.getCandidatesScope(project)
  PsiSearchHelper.getInstance(project).processElementsWithWord(
    { element, offsetInElement ->
      if (element !is XmlTag) {
        return@processElementsWithWord true
      }

      val elementAtOffset = element.findElementAt(offsetInElement)
      if (elementAtOffset == null) {
        return@processElementsWithWord true
      }

      val foundText = elementAtOffset.text
      if (!strictMatch && !StringUtil.contains(foundText, name)) {
        return@processElementsWithWord true
      }
      if (strictMatch && !StringUtil.equals(foundText, name)) {
        return@processElementsWithWord true
      }

      val dom = DomUtil.getDomElement(element) as? Extension ?: return@processElementsWithWord true
      callback(dom, element)
    }, scope, name, UsageSearchContext.IN_FOREIGN_LANGUAGES, true /* case-sensitive */)
}

private fun findCandidatesByClassName(jvmClassName: String, project: Project): List<ExtensionCandidate> {
  val result = SmartList<ExtensionCandidate>()
  val smartPointerManager by lazy { SmartPointerManager.getInstance(project) }
  processExtensionDeclarations(jvmClassName, project, true) { extension, tag ->
    if (extension.extensionPoint != null) {
      result.add(ExtensionCandidate(smartPointerManager.createSmartPsiElementPointer(tag)))
    }
    // continue processing
    true
  }
  return result
}

class ExtensionByExtensionPointLocator : ExtensionLocator {
  private val project: Project
  private val pointQualifiedName: String
  private val extensionId: String?

  constructor(project: Project, extensionPoint: ExtensionPoint, extensionId: String?) {
    this.project = project
    pointQualifiedName = extensionPoint.effectiveQualifiedName
    this.extensionId = extensionId
  }

  constructor(project: Project, extensionPointQualifiedName: String, extensionId: String?) {
    this.project = project
    pointQualifiedName = extensionPointQualifiedName
    this.extensionId = extensionId
  }

  override fun findCandidates(): List<ExtensionCandidate> {
    // We must search for the last part of EP name, because for instance 'com.intellij.console.folding' extension
    // may be declared as <extensions defaultExtensionNs="com"><intellij.console.folding ...
    val epNameToSearch = StringUtil.substringAfterLast(pointQualifiedName, ".") ?: return emptyList()

    val result = SmartList<ExtensionCandidate>()
    val smartPointerManager by lazy { SmartPointerManager.getInstance(project) }
    processExtensionDeclarations(epNameToSearch, project, false) { extension, tag ->
      val ep = extension.extensionPoint ?: return@processExtensionDeclarations true

      if (StringUtil.equals(ep.effectiveQualifiedName, pointQualifiedName) && (extensionId == null || extensionId == extension.id.stringValue)) {
        result.add(ExtensionCandidate(smartPointerManager.createSmartPsiElementPointer(tag)))
        // stop after the first found candidate if ID is specified
        return@processExtensionDeclarations extensionId == null
      }
      true
    }
    return result
  }
}

sealed class ExtensionLocator {
  abstract fun findCandidates(): List<ExtensionCandidate>
}

private class ExtensionByClassLocator(private val project: Project, private val clazz: JvmClass) : ExtensionLocator() {
  override fun findCandidates(): List<ExtensionCandidate> {
    return findCandidatesByClassName(JvmClassUtil.getJvmClassName(clazz) ?: return emptyList(), project)
  }
}

private class ExtensionByPsiClassLocator(private val psiClass: PsiClass) : ExtensionLocator() {
  override fun findCandidates(): List<ExtensionCandidate> {
    return findCandidatesByClassName(ClassUtil.getJVMClassName(psiClass) ?: return emptyList(), psiClass.project)
  }
}