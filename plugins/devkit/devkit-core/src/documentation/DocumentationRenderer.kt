// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk

private const val HEADER_LEVEL = "#####"

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
      .convertCallouts()
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
      }
      else {
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

  private fun CharSequence.convertCallouts(): String {
    val content = StringBuilder()
    val lines = this.lines()
    var i = 0
    while (i < lines.size) {
      val line = lines[i]
      if (line.trim().startsWith("> ")) {
        val calloutStart = i
        i++
        while (i < lines.size) {
          val trimmedLine = lines[i].trim()
          if (trimmedLine.startsWith("> ") || trimmedLine == ">" || trimmedLine.isAttributesLine()) {
            i++
          }
          else {
            break
          }
        }
        content.appendLine(buildCalloutHeader(lines[i]))
        for (j in calloutStart until i) {
          if (!lines[j].isAttributesLine()) {
            content.appendLine(lines[j])
          }
        }
      }
      else {
        content.appendLine(line)
      }
      i++
    }
    return content.toString()
  }

  private fun buildCalloutHeader(attributesLine: String): String {
    val attributes = parseAttributes(attributesLine)
    val indent = attributesLine.takeWhile { it == ' ' }
    val style = attributes["style"]
    val icon = when (style) {
      "warning" -> "AllIcons.General.Warning"
      "info" -> "AllIcons.General.Information"
      else -> "AllIcons.Actions.IntentionBulbGrey"
    }
    val text = attributes["title"] ?: when (style) {
      "warning" -> "Warning"
      "info" -> "Information"
      else -> "Tip"
    }
    return "${indent}> ${HtmlChunk.tag("icon").attr("src", icon)}&nbsp;<b>$text</b><br>"
  }

  private fun String.isAttributesLine(): Boolean {
    return matches(Regex("^\\s*\\{*?}\\s*"))
  }

  private fun parseAttributes(input: String): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    val pattern = """(\w+)\s*=\s*(?:'([^']*)'|"([^"]*)"|(\S+))""".toRegex()
    pattern.findAll(input.trim().removeSurrounding("{", "}")).forEach { matchResult ->
      val (key, singleQuoted, doubleQuoted, unquoted) = matchResult.destructured
      val value = singleQuoted.ifEmpty { doubleQuoted.ifEmpty { unquoted } }
      attributes[key] = value
    }
    return attributes
  }

  private fun StringBuilder.appendElement(element: Element): StringBuilder {
    appendElementPath(element.path)
    appendLine("<hr/>")
    appendDeprecation(element.deprecatedSince, element.deprecationNote)
    appendSinceUntil(element.since, element.until)
    element.description?.trim()?.let { appendLine("$it\n") }
    appendRequirement(element.requirement)
    appendDefaultValue(element.defaultValue)
    appendAttributes(element.attributes)
    appendChildren(element)
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
      append(elementLinkOrWildcard(linkElements[i], linkElements.take(i + 1)))
    }
    if (linkElements.isNotEmpty()) {
      append(" / ")
    }
    val lastElementName = elementPath.last()
    if (linkForLast) {
      append(elementLinkOrWildcard(lastElementName, elementPath))
    }
    else {
      append("**`<$lastElementName>`**")
    }
  }

  private fun elementLinkOrWildcard(text: String, path: List<String>): String {
    if (text != "*") {
      val linkPath = path.toPathString()
      return "[`<$text>`]($ELEMENT_DOC_LINK_PREFIX$linkPath)"
    }
    else {
      return "`*`"
    }
  }

  private fun StringBuilder.appendDeprecation(deprecatedSince: String?, deprecationNote: String?) {
    if (deprecatedSince != null || deprecationNote != null) {
      append("**_")
      if (deprecatedSince != null) {
        append("Deprecated since ${deprecatedSince}")
      }
      else {
        append("Deprecated")
      }
      append("_**")
      val deprecationNote = deprecationNote
      if (deprecationNote != null) {
        append("<br/>")
        val italicDeprecationNote = deprecationNote.lines().joinToString(separator = "\n") { if (it.isNotEmpty()) "_${it}_" else it }
        append(italicDeprecationNote)

      }
      appendLine().appendLine()
    }
  }

  private fun StringBuilder.appendSinceUntil(since: String?, until: String?) {
    if (since != null || until != null) {
      append('_')
      append("Available: ")
      if (since != null) {
        append("since ${since}")
        if (until != null) {
          append(", ")
        }
      }
      if (until != null) {
        append("until ${until}")
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
        appendLine("- ${attributeLink(attribute.name!!, attribute.path)}${getRequirementSimpleText(attribute.requirement)}")
      }
    }
  }

  private fun attributeLink(text: String, path: List<String>): String {
    val linkPath = path.toPathString()
    return "[`$text`]($ATTRIBUTE_DOC_LINK_PREFIX$linkPath)"
  }

  private fun getRequirementSimpleText(requirement: Requirement?): String {
    requirement ?: return ""
    return when (requirement.required) {
      Required.YES -> " _required_"
      Required.YES_FOR_PAID -> " _required for paid/freemium_"
      else -> ""
    }
  }

  private fun StringBuilder.appendChildren(element: Element) {
    if (element.children.isEmpty() && element.childrenDescription == null) return
    appendLine("\n$HEADER_LEVEL Children")
    if (element.childrenDescription != null) {
      appendLine(element.childrenDescription)
      appendParagraphSeparator()
    } else {
      for (child in element.children) {
        val childElement = child.element?.takeIf { !it.isWildcard() } ?: continue
        val linkText = childElement.name
        val linkPath = childElement.path.toPathString()
        appendLine("- [`<$linkText>`]($ELEMENT_DOC_LINK_PREFIX$linkPath)${getRequirementSimpleText(child.element?.requirement)}")
      }
      appendParagraphSeparator()
    }
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
    appendDeprecation(attribute.deprecatedSince, attribute.deprecationNote)
    appendSinceUntil(attribute.since, attribute.until)
    attribute.description?.trim()?.let { append("$it\n") }
    appendParagraphSeparator()
    appendAttributeRequirement(attribute.requirement)
    appendParagraphSeparator()
    attribute.defaultValue?.trim()?.let {
      append("Default value: $it")
    }
    return this
  }

  private fun StringBuilder.appendAttributePath(elementPath: List<String>) {
    val prevElements = elementPath.dropLast(1)
    appendElementPath(prevElements, linkForLast = true)
    append(" / **`@${elementPath.last()}`**")
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
