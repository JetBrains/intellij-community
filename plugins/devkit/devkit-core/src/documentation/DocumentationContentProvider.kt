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
import java.util.concurrent.ConcurrentHashMap

@Service(Level.APP)
internal class DocumentationContentProvider {

  private val contentCache = ConcurrentHashMap<DocumentationDataCoordinates, DocumentationContent?>()

  fun getContent(coordinates: DocumentationDataCoordinates): DocumentationContent? {
    // TODO: downloading from coordinates.url
    return contentCache.computeIfAbsent(coordinates) { loadContentFromResources(coordinates.localPath) }
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
      copyReusedObjectsExceptContainingItself(elementWrapper, null, mutableListOf())
      fillElementPathsRecursively(element, emptyList())
      fillSelfContainingElementsRecursively(elementWrapper)
    }
  }

  /**
   * This is needed for being able to set separate paths for elements that can appear
   * under multiple parents.
   * For example, in plugin.xml, the `<action>` can be located as:
   * - `<idea-plugin>` / `<actions>` / `<action>`
   * - `<idea-plugin>` / `<actions>` / `<group>` / `<action>`
   *
   * We use YAML aliases to avoid data duplication, and SnakeYAML reflects this behavior in
   * parsed objects, so it shares the same instance when it was referenced via anchor in YAML.
   * So in the example case, there would be a single `<action>` element object, and we couldn't
   * set a separate path for each case. For this reason, we copy such elements.
   * We don't copy self-containing elements (for example, `<group>`) as they would be
   * copied infinitely.
   */
  private fun copyReusedObjectsExceptContainingItself(
    elementWrapper: ElementWrapper,
    parentWrapper: ElementWrapper?,
    alreadyUsedElements: MutableList<ElementWrapper>
  ) {
    val element = elementWrapper.element ?: return
    val parent = parentWrapper?.element
    if (parentWrapper != null && parent != null && alreadyUsedElements.contains(elementWrapper) && element.containsItself == false) {
      val elementCopy = element.copy()
      val wrapperCopy = ElementWrapper(elementCopy)
      parent.children = parent.children.replace(elementWrapper, wrapperCopy)
    }
    alreadyUsedElements.add(elementWrapper)
    for (child in element.children) {
      copyReusedObjectsExceptContainingItself(child, elementWrapper, alreadyUsedElements)
    }
  }

  private fun <E> List<E>.replace(old: E, new: E): List<E> {
    return map { if (it === old) new else it }
  }


  private fun fillElementPathsRecursively(element: Element, parentPath: List<String>) {
    val elementPath = parentPath + element.name!!
    // If an element is aliased and referenced in YAML, the same instance is shared.
    // For this reason, set the path only if it is empty, so we get the shortest paths filled.
    if (element.path.isEmpty()) {
      element.path = elementPath
    }
    for (attribute in element.attributes.mapNotNull { it.attribute }) {
      if (attribute.path.isEmpty()) {
        val attributePath = elementPath + attribute.name!!
        attribute.path = attributePath
      }
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
