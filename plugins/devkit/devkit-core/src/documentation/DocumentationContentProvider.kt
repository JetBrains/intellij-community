// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.representer.Representer
import java.util.concurrent.atomic.AtomicReference

@Service(Level.APP)
internal class DocumentationContentProvider {

  private val cachedContent = AtomicReference<DocumentationContent?>(null)

  fun getContent(coordinates: DocumentationDataCoordinates): DocumentationContent? {
    // TODO: downloading from coordinates.url
    cachedContent.get()?.let { return it }
    synchronized(this) {
      cachedContent.get()?.let { return it }
      return cachedContent.updateAndGet { loadContentFromResources(coordinates.localPath) }
    }
  }

  private fun loadContentFromResources(localPath: String): DocumentationContent? {
    val yamlContent = this::class.java.getResourceAsStream(localPath)?.use {
      it.bufferedReader().use { br ->
        br.readText()
      }
    } ?: return null
    val loaderOptions = LoaderOptions().apply {
      isEnumCaseSensitive = false
    }
    val representer = Representer(DumperOptions()).apply {
      propertyUtils.isSkipMissingProperties = true
    }
    val constructor = DescriptorDocumentationConstructor(loaderOptions)
    // DumperOptions pointed in the deprecation message doesn't support skipping missing properties
    return Yaml(constructor, representer).load<DocumentationContent>(yamlContent)
      ?.takeIf { it.elements.isNotEmpty() == true }
  }

  companion object {
    fun getInstance(): DocumentationContentProvider = service()
  }

}

/**
 * Post-processes the parsed content objects.
 */
private class DescriptorDocumentationConstructor(loaderOptions: LoaderOptions) :
  Constructor(DocumentationContent::class.java, loaderOptions) {

  override fun constructObject(node: Node): Any {
    val obj = super.constructObject(node)
    if (obj is DocumentationContent) {
      fillMissingData(obj)
    }
    return obj
  }

  private fun fillMissingData(content: DocumentationContent) {
    for (elementWrapper in content.elements) {
      val element = elementWrapper.element ?: continue
      fillElementPathsRecursively(element, emptyList())
      fillSelfContainingElementsRecursively(elementWrapper)
    }
  }

  private fun fillElementPathsRecursively(element: Element, parentPath: List<String>) {
    val elementPath = parentPath + element.name!!
    element.path = elementPath
    for (attribute in element.attributes.mapNotNull { it.attribute }) {
      val attributePath = elementPath + attribute.name!!
      attribute.path = attributePath
    }
    for (child in element.children) {
      fillElementPathsRecursively(child.element!!, elementPath)
    }
  }

  private fun fillSelfContainingElementsRecursively(elementWrapper: ElementWrapper) {
    val element = elementWrapper.element ?: return
    for (child in element.children) {
      fillSelfContainingElementsRecursively(child)
    }
    if (element.containsItself) {
      element.children = element.children + elementWrapper
    }
  }
}

internal data class DocumentationDataCoordinates(
  /**
   * URL to the documentation YAML on a public server (for example, a public GitHub repository).
   */
  val url: String,
  /**
   * Relative path the documentation YAML in plugin resources.
   */
  val localPath: String,
)
