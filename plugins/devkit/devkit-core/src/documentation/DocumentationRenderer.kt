// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.icons.AllIcons
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import javax.swing.Icon

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
      .deleteInternalLinks()
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

  private fun CharSequence.deleteInternalLinks(): String {
    val internalLinkRegex = Regex("\\[([^]]*)]\\(([^ )]*)\\)\\{internal}")
    val isIntelliJPlatformProject = IntelliJProjectUtil.isIntelliJPlatformProject(project)
    return internalLinkRegex.replace(this) { matchResult ->
      val text = matchResult.groupValues[1]
      val url = matchResult.groupValues[2]
      if (isIntelliJPlatformProject) "[$text]($url)" else text
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
      "warning" -> "AllIcons.General.Warning" to AllIcons.General.Warning
      "info" -> "AllIcons.General.Information" to AllIcons.General.Information
      else -> "AllIcons.Actions.IntentionBulbGrey" to AllIcons.Actions.IntentionBulbGrey
    }
    val text = attributes["title"] ?: when (style) {
      "warning" -> "Warning"
      "info" -> "Information"
      else -> "Tip"
    }
    return "${indent}> ${buildIconTitle(icon.first, icon.second, text)}<br>"
  }

  private fun buildIconTitle(iconId: String, icon: Icon, text: String): String {
    return "${HtmlChunk.icon(iconId, icon)}&nbsp;<b>$text</b>"
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
    appendNamespace(element.namespace)
    appendRequirement(element.requirement)
    appendDefaultValue(element.defaultValue)
    appendAttributes(element.attributes)
    appendChildren(element)
    appendExamples(element.examples)
    appendReferences(element.references)
    appendInternalNote(element.getOwnOrParentInternalNote())
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

  private fun StringBuilder.appendNamespace(namespace: String?) {
    if (namespace == null) return
    appendLine("$HEADER_LEVEL Namespace")
    appendLine("`$namespace`")
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
    val includedAttributes = attributes
      .mapNotNull { it.attribute }
      .filter { it.isIncludedInDocProvider() }
    if (includedAttributes.isNotEmpty()) {
      appendLine("$HEADER_LEVEL Attributes")
      for (attribute in includedAttributes) {
        val attributeDetails = getDetails(attribute.getOwnOrParentInternalNote(), attribute.requirement)
        appendLine("- ${attributeLink(attribute.name!!, attribute.path)}$attributeDetails")
      }
    }
  }

  private fun attributeLink(text: String, path: List<String>): String {
    val linkPath = path.toPathString()
    return "[`$text`]($ATTRIBUTE_DOC_LINK_PREFIX$linkPath)"
  }

  private fun getDetails(internalNote: String?, requirement: Requirement?): String {
    val details = mutableListOf<String>()
    if (internalNote != null) {
      details.add("internal")
    }
    if (requirement != null) {
      val requiredDetails = when (requirement.required) {
        Required.YES -> "required"
        Required.YES_FOR_PAID -> "required for paid/freemium"
        else -> null
      }
      if (requiredDetails != null) {
        details.add("**$requiredDetails**")
      }
    }
    if (details.isEmpty()) return ""
    return details.joinToString(prefix = " ", separator = "; ") { "_${it}_" }
  }

  private fun StringBuilder.appendChildren(element: Element) {
    if (element.children.isEmpty() && element.childrenDescription == null) return
    appendLine("\n$HEADER_LEVEL Children")
    if (element.childrenDescription != null) {
      appendLine(element.childrenDescription)
      appendParagraphSeparator()
    }
    else {
      for (child in element.children) {
        val childElement = child.element?.takeIf { !it.isWildcard() } ?: continue
        if (!childElement.isIncludedInDocProvider()) continue
        val linkText = childElement.name
        val linkPath = childElement.path.toPathString()
        val linkUrl = "$ELEMENT_DOC_LINK_PREFIX$linkPath"
        val childDetails = getDetails(childElement.getOwnOrParentInternalNote(), childElement.requirement)
        appendLine("- [`<$linkText>`]($linkUrl)$childDetails")
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

  private fun StringBuilder.appendInternalNote(internalNote: String?) {
    internalNote ?: return
    appendLine("\n###### ${buildIconTitle("AllIcons.General.Warning", AllIcons.General.Warning, "Internal Use Only")}")
    append(internalNote.trim())
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
    appendInternalNote(attribute.getOwnOrParentInternalNote())
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
