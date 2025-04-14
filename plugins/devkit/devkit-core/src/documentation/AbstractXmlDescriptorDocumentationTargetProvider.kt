// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.icons.AllIcons
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.*
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.siblings
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.reflect.DomAttributeChildDescription

internal const val ELEMENT_PATH_PREFIX = "#element:"
internal const val ATTRIBUTE_PATH_PREFIX = "#attribute:"
internal const val ELEMENT_DOC_LINK_PREFIX = "$PSI_ELEMENT_PROTOCOL$ELEMENT_PATH_PREFIX"
internal const val ATTRIBUTE_DOC_LINK_PREFIX = "$PSI_ELEMENT_PROTOCOL$ATTRIBUTE_PATH_PREFIX"

/**
 * Base class for XML descriptors (for example, plugin.xml) documentation providers.
 * It parses and renders content from a documentation YAML file
 * (for example, `/documentation/plugin-descriptor.yaml`), which is used to render
 * documentation in the IDE and optionally also in
 * [SDK docs](https://plugins.jetbrains.com/docs/intellij).
 *
 * To implement a new documentation provider:
 * 1. Create a documentation YAML containing the descriptor content (the YAML file should be automatically mapped to
 *    the `schemas/descriptor-documentation-schema.json` schema; all elements are documented).
 * 2. Extend this class.
 * 3. Provide a documentation YAML URL and path in [docYamlCoordinates].
 * 4. Register the provider in `com.intellij.platform.backend.documentation.psiTargetProvider` extension point.
 */
internal abstract class AbstractXmlDescriptorDocumentationTargetProvider : PsiDocumentationTargetProvider {

  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    originalElement ?: return null
    if (!isApplicable(element, originalElement)) return null
    val (isAttribute, elementPath) = getIsAttributeAndPath(element, originalElement) ?: return null
    val content = DocumentationContentProvider.getInstance().getContent(docYamlCoordinates) ?: return null
    return if (isAttribute) {
      val docAttribute = content.findAttribute(elementPath)?.takeIf { it.isIncludedInDocProvider() } ?: return null
      XmlDescriptorAttributeDocumentationTarget(element.project, content, docAttribute)
    }
    else {
      val docElement = content.findElement(elementPath)?.takeIf { !it.isWildcard() && it.isIncludedInDocProvider() } ?: return null
      XmlDescriptorElementDocumentationTarget(element.project, content, docElement)
    }
  }

  abstract fun isApplicable(element: PsiElement, originalElement: PsiElement?): Boolean

  private fun getIsAttributeAndPath(element: PsiElement, originalElement: PsiElement): Pair<Boolean, List<String>>? {
    val context = findContextElement(originalElement) ?: return null
    val elementName = getElementName(element) ?: return null
    if (elementName == getContextElementName (context)) { // assume no parent and child with the same name
      return (context is XmlAttribute) to getXmlElementPath(context)
    }
    // handle lookup element
    val target = (element as? PomTargetPsiElement)?.target ?: return null
    val isAttribute = target is DomAttributeChildDescription<*>
    val parentTag = context.parentOfType<XmlTag>(withSelf = isAttribute) ?: return null
    val parentPath = getXmlElementPath(parentTag)
    return isAttribute to (parentPath + elementName)
  }

  private fun getElementName(element: PsiElement): String? {
    // because in case of elements defined with XSD:
    if (element is XmlTag && element.containingFile.virtualFile.extension == "xsd") {
      return element.getAttribute("name")?.value
    }
    return (element as? PsiNamedElement)?.name
  }

  private fun getContextElementName(context: PsiElement): String? {
    return (context as? XmlTag)?.localName ?: (context as? PsiNamedElement)?.name
  }

  private fun findContextElement(context: PsiElement): XmlElement? {
    if (context is PsiWhiteSpace) {
      val prevXmlElement = context.siblings(forward = false, withSelf = false)
        .mapNotNull { it as? XmlTag }
        .firstOrNull()
      if (prevXmlElement != null) {
        return prevXmlElement
      }
    }
    return generateSequence(context) { it.parent }
      .filter { it is XmlTag || it is XmlAttribute }
      .filterIsInstance<XmlElement>()
      .firstOrNull()
  }

  private fun getXmlElementPath(element: PsiElement): List<String> =
    generateSequence(element) { it.parent }
      .takeWhile { it is XmlTag || it is XmlAttribute }
      .mapNotNull { (it as PsiNamedElement).name }
      .toList()
      .asReversed()

  abstract val docYamlCoordinates: DocumentationDataCoordinates

}

private abstract class AbstractXmlDescriptorDocumentationTarget(
  val project: Project,
  val presentation: String,
  /**
   * The same content is used in subsequent documentation popup browser requests intentionally
   * (see [XmlDescriptorDocumentationLinkHandler]).
   * It could happen that some element was removed in YAML and clicking its link could result in an error.
   */
  val content: DocumentationContent,
) : DocumentationTarget {

  override fun createPointer(): Pointer<out DocumentationTarget> =
    Pointer.hardPointer(this)

  @Suppress("HardCodedStringLiteral")
  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder(presentation)
      .icon(AllIcons.Nodes.Tag)
      .presentation()

  fun getRenderer(): DocumentationRenderer =
    project.getService(DocumentationRenderer::class.java)
}

private class XmlDescriptorElementDocumentationTarget(project: Project, content: DocumentationContent, private val element: Element) :
  AbstractXmlDescriptorDocumentationTarget(project, element.name!!, content) {
  override fun computeDocumentation(): DocumentationResult {
    return DocumentationResult.asyncDocumentation {
      DocumentationResult.documentation(getRenderer().renderElement(element, content.baseUrl))
    }
  }
}

private class XmlDescriptorAttributeDocumentationTarget(project: Project, content: DocumentationContent, private val attribute: Attribute) :
  AbstractXmlDescriptorDocumentationTarget(project, attribute.getPresentableName(), content) {
  override fun computeDocumentation(): DocumentationResult {
    return DocumentationResult.asyncDocumentation {
      DocumentationResult.documentation(getRenderer().renderAttribute(attribute, content.baseUrl))
    }
  }
}

internal class XmlDescriptorDocumentationLinkHandler : DocumentationLinkHandler {

  override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
    if (target is AbstractXmlDescriptorDocumentationTarget) {
      when {
        url.startsWith(ELEMENT_DOC_LINK_PREFIX) -> {
          val elementPath = urlToPath(url, ELEMENT_DOC_LINK_PREFIX)
          val element = target.content.findElement(elementPath) ?: return null
          return LinkResolveResult.resolvedTarget(
            XmlDescriptorElementDocumentationTarget(target.project, target.content, element)
          )
        }
        url.startsWith(ATTRIBUTE_DOC_LINK_PREFIX) -> {
          val attributePath = urlToPath(url, ATTRIBUTE_DOC_LINK_PREFIX)
          val attribute = target.content.findAttribute(attributePath) ?: return null
          return LinkResolveResult.resolvedTarget(
            XmlDescriptorAttributeDocumentationTarget(target.project, target.content, attribute)
          )
        }
        else -> {
          // required for Java links to work; inspired by PsiDocumentationLinkHandler
          val project = target.project
          val resolved = DocumentationManager.targetAndRef(project, url, null)?.first ?: return null // we can't get rid of this API usage
          return LinkResolveResult.resolvedTarget(psiDocumentationTargets(resolved, null).first())
        }
      }
    }
    return null
  }

  private fun urlToPath(url: String, prefix: String): List<String> {
    return url.substring(prefix.length).split("__")
  }
}
