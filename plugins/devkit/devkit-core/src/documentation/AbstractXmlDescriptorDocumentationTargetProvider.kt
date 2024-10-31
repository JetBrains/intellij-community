// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

const val ELEMENT_PATH_PREFIX = "#element:"
const val ATTRIBUTE_PATH_PREFIX = "#attribute:"
const val ELEMENT_DOC_LINK_PREFIX = "$PSI_ELEMENT_PROTOCOL$ELEMENT_PATH_PREFIX"
const val ATTRIBUTE_DOC_LINK_PREFIX = "$PSI_ELEMENT_PROTOCOL$ATTRIBUTE_PATH_PREFIX"

/**
 * Base class for XML descriptors (for example, plugin.xml) documentation providers.
 * It parses and renders content from a documentation YAML file
 * (for example, `/documentation/plugin-descriptor.yaml`), which is used to render
 * documentation in the IDE and optionally also in
 * [SDK docs](https://plugins.jetbrains.com/docs/intellij).
 *
 * To implement a new documentation provider:
 * 1. Create a documentation YAML containing the descriptor content.
 * 2. Extend this class.
 * 3. Provide a documentation YAML URL and path in [docYamlCoordinates].
 * 4. Register the provider in `com.intellij.platform.backend.documentation.psiTargetProvider` extension point.
 */
internal abstract class AbstractXmlDescriptorDocumentationTargetProvider : PsiDocumentationTargetProvider {

  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    originalElement ?: return null
    if (!isApplicable(element, originalElement)) return null
    val parent = originalElement.parent
    val xmlElement = parent as? XmlAttribute ?: parent as? XmlTag ?: return null
    val elementPath = getXmlElementPath(xmlElement)
    val content = DocumentationContentProvider.getInstance().getContent(docYamlCoordinates) ?: return null
    return when (xmlElement) {
      is XmlTag -> {
        val docElement = content.findElement(elementPath) ?: return null
        XmlDescriptorElementDocumentationTarget(element.project, content, docElement)
      }
      is XmlAttribute -> {
        val docAttribute = content.findAttribute(elementPath) ?: return null
        XmlDescriptorAttributeDocumentationTarget(element.project, content, docAttribute)
      }
      else -> null
    }
  }

  abstract fun isApplicable(element: PsiElement, originalElement: PsiElement?): Boolean

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
  AbstractXmlDescriptorDocumentationTarget(project, attribute.name!!, content) {
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
      }
    }
    return null
  }

  private fun urlToPath(url: String, prefix: String): List<String> {
    return url.substring(prefix.length).split("__")
  }
}
