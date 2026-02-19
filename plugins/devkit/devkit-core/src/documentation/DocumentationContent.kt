// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    val currentElement = currentElements.find { it.element?.name == name || it.element?.isWildcard() == true }?.element ?: return null
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

internal fun DocumentationItem.isIncludedInDocProvider(): Boolean {
  return this.shouldBeRenderedIn(RenderContext.DOC_PROVIDER)
}

// allows for referencing elements by anchors in YAML
internal data class ElementWrapper(
  var element: Element? = null
)

internal interface DocumentationItem {
  val parent: DocumentationItem?
  val renderContexts: List<RenderContext>
  val internalNote: String?

  /**
   * Determines whether the current documentation item should be rendered in the specified render context.
   * If any parent doesn't include the context, then this item is also not included, even if it overrides
   * the [renderContexts].
   *
   * @param context The render context to check against.
   * @return `true` if the documentation item should be rendered in the specified render context,
   *         `false` otherwise.
   */
  fun shouldBeRenderedIn(context: RenderContext): Boolean {
    generateSequence(this) { it.parent }.toList().reversed().forEach {
      if (!it.renderContexts.contains(context)) {
        return false
      }
    }
    return true
  }

  fun getOwnOrParentInternalNote(): String? {
      return internalNote ?: parent?.getOwnOrParentInternalNote()
  }
}

internal data class Element(
  var name: String? = null,
  var descriptiveName: String? = null,
  var sdkDocsFixedPath: List<String> = emptyList(),
  var namespace: String? = null,
  var since: String? = null,
  var until: String? = null,
  var deprecatedSince: String? = null,
  var deprecationNote: String? = null,
  var description: String? = null,
  override var internalNote: String? = null,
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
  override var parent: DocumentationItem? = null,
  override var renderContexts: List<RenderContext> = RenderContext.entries, // included in all by default
) : DocumentationItem {

  fun isWildcard(): Boolean {
    return name == "*"
  }

  fun copy(): Element {
    return this.copy(attributes = this.attributes.map { it.copy() })
  }

  override fun toString(): String {
    return "Element(name=$name, path=$path)"
  }
}

// allows for referencing attributes by anchors in YAML
internal data class AttributeWrapper(
  var attribute: Attribute? = null,
) {
  fun copy(): AttributeWrapper {
    return this.copy(attribute = this.attribute?.copy())
  }
}

internal data class Attribute(
  var name: String? = null,
  var since: String? = null,
  var until: String? = null,
  var deprecatedSince: String? = null,
  var deprecationNote: String? = null,
  var requirement: Requirement? = null,
  var description: String? = null,
  override var internalNote: String? = null,
  var defaultValue: String? = null,
  var path: List<String> = emptyList(),
  override var parent: DocumentationItem? = null,
  override var renderContexts: List<RenderContext> = RenderContext.entries, // included in all by default
) : DocumentationItem {
  fun getPresentableName(): String {
    val elementName = path[path.lastIndex - 1]
    return "$elementName@$name"
  }

  override fun toString(): String {
    return "Attribute(name=$name, path=$path)"
  }
}

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

internal enum class RenderContext {
  SDK_DOCS,
  DOC_PROVIDER
}
