// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

private const val HEADER_LEVEL = "#####"

// FIXME: group@class - class links don't work

@Service(Level.PROJECT)
internal class DocumentationRenderer(private val project: Project) {

  private val writersideTagReplacements = listOf(
    "control" to "b",
    "path" to "i",
    "ui-control" to "b",
    "ui-path" to "b"
  ).map { Regex("&lt;${it.first}&gt;(.*?)&lt;/${it.first}&gt;") to it.second }

  @NlsSafe
  fun renderElement(element: Element, baseUrl: String): String {
    return StringBuilder().appendElement(element).toDocHtml(baseUrl, element.path)
  }

  private fun StringBuilder.toDocHtml(baseUrl: String, elementPath: List<String>): String {
    @Suppress("HardCodedStringLiteral")
    val markdownContent = this
      .adjustLinks(baseUrl)
      .deleteSelfLinks(elementPath)
      .adjustClassLinks()
      .deleteCalloutAttributes()
    return runReadAction { DocMarkdownToHtmlConverter.convert(project, markdownContent) }
      .removeWritersideSpecificTags()
  }

  private fun CharSequence.adjustLinks(baseUrl: String): String {
    val markdownLinkRegex = Regex("\\[(.*?)]\\((.*?)\\)")
    return markdownLinkRegex.replace(this) { matchResult ->
      val text = matchResult.groupValues[1]
      val url = matchResult.groupValues[2]
      if (url.startsWith('#')) {
        when {
          url.startsWith(ELEMENT_PATH_PREFIX) || url.startsWith(ATTRIBUTE_PATH_PREFIX) -> {
            "[$text]($PSI_ELEMENT_PROTOCOL$url)"
          }
          else -> "[$text]($baseUrl$url)"
        }
      }
      else {
        matchResult.value
      }
    }
  }

  private fun CharSequence.deleteSelfLinks(elementPath: List<String>): String {
    val elementPathString = elementPath.toPathString()
    val elementLinkRegex = Regex("\\[(.*?)]\\(psi_element://(#.*?)\\)")
    return elementLinkRegex.replace(this) { matchResult ->
      val text = matchResult.groupValues[1]
      val url = matchResult.groupValues[2]
      if (url.startsWith(ELEMENT_PATH_PREFIX) || url.startsWith(ATTRIBUTE_PATH_PREFIX)) {
        val rawPath = url.removePrefix(ELEMENT_PATH_PREFIX).removePrefix(ATTRIBUTE_PATH_PREFIX)
        if (rawPath == elementPathString) text else "[$text]($PSI_ELEMENT_PROTOCOL$url)"
      } else {
        matchResult.value
      }
    }
  }

  private fun CharSequence.adjustClassLinks(): String {
    val attributeLinkRegex = Regex("\\[(.*?)]\\(.*?\\)\\{fqn=\"(.*?)\"}")
    return attributeLinkRegex.replace(this) { matchResult ->
      val text = matchResult.groupValues[1]
      val fqn = matchResult.groupValues[2]
      "[$text]($PSI_ELEMENT_PROTOCOL$fqn)"
    }
  }

  private fun CharSequence.deleteCalloutAttributes(): String {
    return this.replace(Regex("\\{style=.*?}"), "")
  }

  private fun StringBuilder.appendElement(element: Element): StringBuilder {
    appendElementPath(element.path)
    appendLine("<hr/>")
    appendDeprecation(element)
    appendSinceUntil(element)
    element.description?.trim()?.let { appendLine("$it\n") }
    appendRequirement(element.requirement)
    appendDefaultValue(element.defaultValue)
    appendAttributes(element.attributes)
    appendChildren(element.children)
    appendExamples(element.examples)
    appendReferences(element.references)
    return this
  }

  private fun StringBuilder.appendElementPath(elementPath: List<String>, linkForLast: Boolean = false) {
    val linkElements = elementPath.dropLast(1).toMutableList()
    for (i in 0 until linkElements.size) {
      if (i > 0) {
        append(" / ")
      }
      append(elementLink(linkElements[i], linkElements.take(i + 1)))
    }
    if (linkElements.isNotEmpty()) {
      append(" / ")
    }
    val lastElementName = elementPath.last()
    if (linkForLast) {
      append(elementLink(lastElementName, elementPath))
    }
    else {
      append("**`<$lastElementName>`**")
    }
  }

  private fun elementLink(text: String, path: List<String>): String {
    val linkPath = path.toPathString()
    return "[`<$text>`]($ELEMENT_DOC_LINK_PREFIX$linkPath)"
  }

  private fun StringBuilder.appendDeprecation(element: Element) {
    if (element.deprecatedSince != null) {
      append("**_")
      append("Deprecated since ${element.deprecatedSince}")
      append("_**")
      val deprecationNote = element.deprecationNote
      if (deprecationNote != null) {
        append("<br/>")
        val italicDeprecationNote = deprecationNote.lines().joinToString(separator = "\n") { if (it.isNotEmpty()) "_${it}_" else it }
        append(italicDeprecationNote)

      }
      appendLine().appendLine()
    }
  }

  private fun StringBuilder.appendSinceUntil(element: Element) {
    if (element.since != null || element.until != null) {
      append('_')
      append("Available: ")
      if (element.since != null) {
        append("since ${element.since}")
        if (element.until != null) {
          append(", ")
        }
      }
      if (element.until != null) {
        append("until ${element.until}")
      }
      appendLine('_')
      appendLine()
    }
  }

  private fun StringBuilder.appendRequirement(requirement: Requirement?) {
    if (requirement == null) return
    val requiredText = when (requirement.required) {
      Required.YES -> "**yes**"
      Required.NO -> "no"
      Required.YES_FOR_PAID -> "only for paid or freemium plugins"
      Required.UNKNOWN -> null
    }
    appendLine("$HEADER_LEVEL Requirement")
    append("Required: ")
    if (requiredText != null) {
      append(requiredText)
      if (requirement.details.isNotEmpty()) {
        append("; ")
      }
    }
    for ((index, detail) in requirement.details.withIndex()) {
      append(detail.trim()).appendLine(if (index != requirement.details.lastIndex) "<br/>" else "")
    }
    appendLine().appendLine()
  }

  private fun StringBuilder.appendDefaultValue(defaultValue: String?) {
    if (defaultValue == null) return
    appendLine("$HEADER_LEVEL Default value")
    appendLine(defaultValue)
  }

  private fun StringBuilder.appendAttributes(attributes: List<AttributeWrapper>) {
    if (attributes.isNotEmpty()) {
      appendLine("$HEADER_LEVEL Attributes")
      for (attribute in attributes.mapNotNull { it.attribute }) {
        appendLine("- ${attributeLink(attribute.name!!, attribute.path)}")
      }
    }
  }

  private fun attributeLink(text: String, path: List<String>): String {
    val linkPath = path.toPathString()
    return "[`$text`]($ATTRIBUTE_DOC_LINK_PREFIX$linkPath)"
  }

  private fun StringBuilder.appendChildren(children: List<ElementWrapper>) {
    if (children.isEmpty()) return
    appendLine("\n$HEADER_LEVEL Children")
    for (child in children) {
      val childElement = child.element ?: continue
      val linkText = childElement.name
      val linkPath = childElement.path.toPathString()
      appendLine("- [`<$linkText>`]($ELEMENT_DOC_LINK_PREFIX$linkPath)")
    }
    appendParagraphSeparator()
  }

  private fun StringBuilder.appendExamples(examples: List<String>?) {
    if (examples == null) return
    if (examples.size == 1) {
      val example = examples.first()
      appendLine("\n$HEADER_LEVEL Example")
      appendLine(example.trim())
    }
    else if (examples.size > 1) {
      appendLine("\n$HEADER_LEVEL Examples")
      for (example in examples) {
        appendLine("- ${example.trim()}")
      }
    }
  }

  private fun StringBuilder.appendReferences(references: List<String>) {
    if (references.isEmpty()) return
    appendLine("$HEADER_LEVEL Reference")
    append(references.joinToString(separator = "\n") { "- $it" })
  }

  @NlsSafe
  fun renderAttribute(attribute: Attribute, baseUrl: String): String {
    return StringBuilder().appendAttribute(attribute).toDocHtml(baseUrl, attribute.path)
  }

  private fun StringBuilder.appendAttribute(attribute: Attribute): StringBuilder {
    appendAttributePath(attribute.path)
    appendLine("<hr/>")
    attribute.description?.trim()?.let { append(it) }
    appendParagraphSeparator()
    appendAttributeRequirement(attribute.requirement)
    appendParagraphSeparator()
    attribute.defaultValue?.trim()?.let {
      append("Default value: `$it`")
    }
    return this
  }

  private fun StringBuilder.appendAttributePath(elementPath: List<String>) {
    val prevElements = elementPath.dropLast(1)
    appendElementPath(prevElements, linkForLast = true)
    append(" : **`${elementPath.last()}`**")
  }

  private fun StringBuilder.appendAttributeRequirement(requirement: Requirement?) {
    if (requirement == null) return
    if (requirement.required == Required.UNKNOWN) {
      if (requirement.details.isNotEmpty()) {
        append("Required: ")
        append(requirement.details.joinToString(separator = "; ") { it.trim() })
      }
    }
    else {
      append("Required: ")
      append(when (requirement.required) {
               Required.YES -> "**yes**"
               Required.NO -> "no"
               Required.YES_FOR_PAID -> "only for paid or freemium plugins"
               Required.UNKNOWN -> throw IllegalStateException("Unknown requirement") // handled in the first `if`
             })
      if (requirement.details.isNotEmpty()) {
        append("; ")
        append(requirement.details.joinToString(separator = "; ") { it.trim() })
      }
    }
  }

  private fun StringBuilder.appendParagraphSeparator() {
    appendLine().appendLine()
  }

  private fun String.removeWritersideSpecificTags(): String {
    var result = this
    for ((writersideTagName, replacementMarker) in writersideTagReplacements) {
      result = result.replace(writersideTagName) { matchResult ->
        val token = matchResult.groupValues[1]
        if (replacementMarker.isEmpty()) token else "<$replacementMarker>$token</$replacementMarker>"
      }
    }
    return result
  }

  private fun List<String>.toPathString(): String {
    return joinToString(separator = "__")
  }

}
