// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

internal data class DocumentationContent(
  var baseUrl: String = "",
  var elements: List<ElementWrapper> = emptyList(),
) {

  fun findElement(path: List<String>): Element? {
    return findElementRecursively(elements, path)
  }

  private fun findElementRecursively(currentElements: List<ElementWrapper>, path: List<String>): Element? {
    if (path.isEmpty()) return null
    val name = path.first()
    val remainingPath = path.drop(1)
    val currentElement = currentElements.find { it.element?.name == name }?.element ?: return null
    return if (remainingPath.isEmpty()) currentElement else findElementRecursively(currentElement.children, remainingPath)
  }

  fun findAttribute(path: List<String>): Attribute? {
    if (path.isEmpty()) return null
    val elementPath = path.dropLast(1)
    val attributeName = path.last()
    val parentElement = findElement(elementPath)
    return parentElement?.attributes?.mapNotNull { it.attribute }?.find { it.name == attributeName }
  }
}

// allows for referencing elements by anchors in YAML
internal data class ElementWrapper(
  var element: Element? = null
)

internal data class Element(
  var name: String? = null,
  var sdkDocsFixedPath: List<String> = emptyList(),
  var since: String? = null,
  var until: String? = null,
  var deprecated: Boolean = false,
  var deprecationNote: String? = null,
  var description: String? = null,
  var sdkDocsSupportDetails: String? = null,
  var attributes: List<AttributeWrapper> = emptyList(),
  var containsItself: Boolean = false,
  var childrenDescription: String? = null,
  var children: List<ElementWrapper> = emptyList(),
  var references: List<String> = emptyList(),
  var requirement: Requirement? = null,
  var defaultValue: String? = null,
  var examples: List<String> = emptyList(),
  var path: List<String> = emptyList(),
)

// allows for referencing attributes by anchors in YAML
internal data class AttributeWrapper(
  var attribute: Attribute? = null,
)

internal data class Attribute(
  var name: String? = null,
  var since: String? = null,
  var until: String? = null,
  var requirement: Requirement? = null,
  var description: String? = null,
  var defaultValue: String? = null,
  var path: List<String> = emptyList(),
)

internal data class Requirement(
  var required: Required = Required.UNKNOWN,
  var details: List<String> = emptyList(),
)

internal enum class Required {
  YES,
  NO,
  YES_FOR_PAID,
  UNKNOWN
}
