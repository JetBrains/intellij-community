// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.PerformanceAssertions
import com.intellij.util.Processor
import com.intellij.util.SmartList
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.GenericAttributeValue
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex
import java.util.*
import java.util.function.Function

fun locateExtensionsByPsiClass(psiClass: PsiClass): List<ExtensionCandidate> {
  return findExtensionsByClassName(psiClass.project, ClassUtil.getJVMClassName(psiClass) ?: return emptyList())
}

fun locateExtensionsByExtensionPoint(extensionPoint: ExtensionPoint): List<ExtensionCandidate> {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, null).findCandidates()
}

fun locateExtensionsByExtensionPointAndId(extensionPoint: ExtensionPoint, extensionId: String): ExtensionLocator {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, extensionId)
}

/**
 * @param extensionIdFunction in case EP is located via custom attribute instead of [Extension.getId].
 */
fun locateExtensionsByExtensionPointAndId(
  extensionPoint: ExtensionPoint,
  extensionId: String,
  extensionIdFunction: (Extension) -> String?,
): ExtensionLocator {
  return ExtensionByExtensionPointLocator(extensionPoint.xmlTag.project, extensionPoint, extensionId, extensionIdFunction)
}

fun processAllExtensionCandidates(
  project: Project,
  extensionPointFqn: String,
  processor: Processor<in Extension>,
) {
  processExtensionCandidates(project, extensionPointFqn, processor, null, null)
}

fun findExtensionCandidate(
  project: Project,
  extensionPointFqn: String,
  processor: Processor<in Extension>,
  extensionPointId: String?,
  nameElementFunction: Function<Extension, GenericAttributeValue<String>?>?,
) {
  processExtensionCandidates(project, extensionPointFqn, processor, extensionPointId, nameElementFunction)
}

/**
 * @param extensionPointId To locate a specific extension instance by ID or `null` to process all.
 * @param nameElementFunction Returns the DOM `id` element when [extensionPointId] is passed.
 */
private fun processExtensionCandidates(
  project: Project,
  extensionPointFqn: String,
  processor: Processor<in Extension>,
  extensionPointId: String?,
  nameElementFunction: Function<Extension, GenericAttributeValue<String>?>?,
) {
  val extensionPointDomElement =
    ExtensionPointIndex.findExtensionPoint(project, PluginRelatedLocatorsUtils.getCandidatesScope(project), extensionPointFqn)
  if (extensionPointDomElement == null) return

  val candidates: List<ExtensionCandidate>
  if (extensionPointId == null) {
    candidates = locateExtensionsByExtensionPoint(extensionPointDomElement)
  }
  else {
    candidates = locateExtensionsByExtensionPointAndId(extensionPointDomElement, extensionPointId) { extension: Extension ->
      val nameElement = nameElementFunction!!.apply(extension)
      nameElement?.getStringValue()
    }.findCandidates()
  }

  val manager = DomManager.getDomManager(project)
  for (candidate in candidates) {
    val element = candidate.pointer.getElement()
    val domElement = manager.getDomElement(element)
    if (domElement is Extension) {
      if (!processor.process(domElement)) return
    }
  }
}

// TODO consider converting to a stream-like entity to avoid IDEA-277738, EA-139648, etc.
/**
 * A synchronized collection should be used as an accumulator in callbacks.
 */
@JvmOverloads
fun processExtensionDeclarations(
  name: String,
  project: Project,
  strictMatch: Boolean = true,
  scope: SearchScope = PluginRelatedLocatorsUtils.getCandidatesScope(project),
  callback: (Extension, XmlTag) -> Boolean,
) {
  PerformanceAssertions.assertDoesNotAffectHighlighting()

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
    }, scope, searchWord, UsageSearchContext.IN_FOREIGN_LANGUAGES, /* caseSensitive = */ true)
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

internal inline fun processExtensionsByClassName(
  project: Project,
  className: String,
  crossinline processor: (XmlTag, ExtensionPoint) -> Boolean,
) {
  processExtensionDeclarations(className, project) { extension, tag ->
    extension.extensionPoint?.let { processor(tag, it) } ?: true
  }
}

private class ExtensionByExtensionPointLocator(
  private val project: Project,
  extensionPoint: ExtensionPoint,
  private val extensionId: String?,
  private val extensionIdFunction: (Extension) -> String? = { extension -> extension.id.stringValue },
) : ExtensionLocator() {
  private val pointQualifiedName = extensionPoint.effectiveQualifiedName

  private fun processCandidates(processor: (XmlTag) -> Boolean) {
    val strictMatch: Boolean
    val searchText: String
    if (extensionId != null) {
      // search for exact match of extensionId as there should be 0..1 occurrences (instead of n extensions)
      searchText = extensionId
      strictMatch = true
    }
    else {
      // We must search for the last part of EP name, because for instance 'com.intellij.console.folding' extension
      // may be declared as <extensions defaultExtensionNs="com"><intellij.console.folding ...
      searchText = StringUtil.substringAfterLast(pointQualifiedName, ".") ?: return
      strictMatch = false
    }
    processExtensionDeclarations(searchText, project, strictMatch) { extension, tag ->
      val ep = extension.extensionPoint ?: return@processExtensionDeclarations true
      if (ep.effectiveQualifiedName == pointQualifiedName &&
          (extensionId == null || extensionId == extensionIdFunction.invoke(extension))) {
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