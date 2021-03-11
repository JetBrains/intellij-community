// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import java.util.*

fun locateExtensionsByPsiClass(psiClass: PsiClass): List<ExtensionCandidate> {
  return findExtensionsByClassName(psiClass.project, ClassUtil.getJVMClassName(psiClass) ?: return emptyList())
}

fun locateExtensionsByExtensionPoint(extensionPoint: ExtensionPoint): List<ExtensionCandidate> {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, null).findCandidates()
}

fun locateExtensionsByExtensionPointAndId(extensionPoint: ExtensionPoint, extensionId: String): ExtensionLocator {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, extensionId)
}

internal fun processExtensionDeclarations(name: String, project: Project, strictMatch: Boolean = true, callback: (Extension, XmlTag) -> Boolean) {
  val scope = PluginRelatedLocatorsUtils.getCandidatesScope(project)
  val searchWord = name.substringBeforeLast('$')
  if (searchWord.isEmpty()) return
  PsiSearchHelper.getInstance(project).processElementsWithWord(
    { element, offsetInElement ->
      val elementAtOffset = (element as? XmlTag)?.findElementAt(offsetInElement) ?: return@processElementsWithWord true
      if (strictMatch) {
        if (!elementAtOffset.textMatches(name)) {
          return@processElementsWithWord true
        }
      }
      else if (!StringUtil.contains(elementAtOffset.text, name)) {
        return@processElementsWithWord true
      }

      val extension = DomManager.getDomManager(project).getDomElement(element) as? Extension ?: return@processElementsWithWord true
      callback(extension, element)
    }, scope, searchWord, UsageSearchContext.IN_FOREIGN_LANGUAGES, /* case-sensitive = */ true)
}

private fun findExtensionsByClassName(project: Project, className: String): List<ExtensionCandidate> {
  val result = Collections.synchronizedList(SmartList<ExtensionCandidate>())
  val smartPointerManager by lazy { SmartPointerManager.getInstance(project) }
  processExtensionsByClassName(project, className) { tag, _ ->
    result.add(ExtensionCandidate(smartPointerManager.createSmartPsiElementPointer(tag)))
    true
  }
  return result
}

internal inline fun processExtensionsByClassName(project: Project, className: String, crossinline processor: (XmlTag, ExtensionPoint) -> Boolean) {
  processExtensionDeclarations(className, project) { extension, tag ->
    extension.extensionPoint?.let { processor(tag, it) } ?: true
  }
}

internal class ExtensionByExtensionPointLocator(private val project: Project,
                                                extensionPoint: ExtensionPoint,
                                                private val extensionId: String?) : ExtensionLocator() {
  private val pointQualifiedName = extensionPoint.effectiveQualifiedName

  private fun processCandidates(processor: (XmlTag) -> Boolean) {
    // We must search for the last part of EP name, because for instance 'com.intellij.console.folding' extension
    // may be declared as <extensions defaultExtensionNs="com"><intellij.console.folding ...
    val epNameToSearch = StringUtil.substringAfterLast(pointQualifiedName, ".") ?: return
    processExtensionDeclarations(epNameToSearch, project, false /* not strict match */) { extension, tag ->
      val ep = extension.extensionPoint ?: return@processExtensionDeclarations true
      if (ep.effectiveQualifiedName == pointQualifiedName && (extensionId == null || extensionId == extension.id.stringValue)) {
        // stop after the first found candidate if ID is specified
        processor(tag) && extensionId == null
      }
      else {
        true
      }
    }
  }

  override fun findCandidates(): List<ExtensionCandidate> {
    val result = Collections.synchronizedList(SmartList<ExtensionCandidate>())
    val smartPointerManager by lazy { SmartPointerManager.getInstance(project) }
    processCandidates {
      result.add(ExtensionCandidate(smartPointerManager.createSmartPsiElementPointer(it)))
    }
    return result
  }
}

sealed class ExtensionLocator {
  abstract fun findCandidates(): List<ExtensionCandidate>
}