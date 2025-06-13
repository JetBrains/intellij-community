package com.intellij.polySymbols.documentation.impl

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolsBundle
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.impl.scaleToHeight
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.Icon

internal data class PolySymbolDocumentationImpl(
  override val name: String,
  override val definition: String,
  override val definitionDetails: String?,
  override val description: @Nls String?,
  override val docUrl: String?,
  override val apiStatus: PolySymbolApiStatus?,
  override val defaultValue: String?,
  override val library: String?,
  override val icon: Icon?,
  override val descriptionSections: Map<@Nls String, @Nls String>,
  override val footnote: @Nls String?,
  override val header: @Nls String?,
) : PolySymbolDocumentation {
  override fun isNotEmpty(): Boolean =
    name != definition || description != null || docUrl != null || (apiStatus != null && apiStatus != PolySymbolApiStatus.Stable)
    || defaultValue != null || library != null || descriptionSections.isNotEmpty() || footnote != null
    || header != null

  override fun withName(name: String): PolySymbolDocumentation =
    copy(name = name)

  override fun withDefinition(definition: String): PolySymbolDocumentation =
    copy(definition = definition)

  override fun withDefinitionDetails(definitionDetails: String?): PolySymbolDocumentation =
    copy(definitionDetails = definitionDetails)

  override fun withDescription(description: @Nls String?): PolySymbolDocumentation =
    copy(description = description)

  override fun withDocUrl(docUrl: String?): PolySymbolDocumentation =
    copy(docUrl = docUrl)

  override fun withApiStatus(apiStatus: PolySymbolApiStatus?): PolySymbolDocumentation =
    copy(apiStatus = apiStatus)

  override fun withDefault(defaultValue: String?): PolySymbolDocumentation =
    copy(defaultValue = defaultValue)

  override fun withLibrary(library: String?): PolySymbolDocumentation =
    copy(library = library)

  override fun withIcon(icon: Icon?): PolySymbolDocumentation =
    copy(icon = icon)

  override fun withDescriptionSection(@Nls name: String, @Nls contents: String): PolySymbolDocumentation =
    copy(descriptionSections = descriptionSections + Pair(name, contents))

  override fun withDescriptionSections(sections: Map<@Nls String, @Nls String>): PolySymbolDocumentation =
    copy(descriptionSections = descriptionSections + sections)

  override fun withFootnote(@Nls footnote: String?): PolySymbolDocumentation =
    copy(footnote = footnote)

  override fun withHeader(header: @Nls String?): PolySymbolDocumentation =
    copy(header = header)

  override fun build(origin: PolySymbolOrigin): DocumentationResult {
    val url2ImageMap = mutableMapOf<String, Image>()

    @Suppress("HardCodedStringLiteral")
    val contents = StringBuilder()
      .appendHeader()
      .appendDefinition(url2ImageMap)
      .appendDescription()
      .appendSections()
      .appendFootnote()
      .toString()
      .loadLocalImages(origin, url2ImageMap)
    return DocumentationResult.documentation(contents).images(url2ImageMap).externalUrl(docUrl)
      .definitionDetails(definitionDetails)
  }

  private fun StringBuilder.appendDefinition(url2ImageMap: MutableMap<String, Image>): StringBuilder =
    append(DocumentationMarkup.DEFINITION_START)
      .also {
        icon?.let { appendIcon(it, url2ImageMap).append("&nbsp;&nbsp;") }
      }
      .append(definition)
      .append(DocumentationMarkup.DEFINITION_END)
      .append('\n')

  private fun StringBuilder.appendDescription(): StringBuilder =
    description?.let {
      append(DocumentationMarkup.CONTENT_START).append('\n')
        .append(it).append('\n')
        .append(DocumentationMarkup.CONTENT_END)
    }
    ?: this

  private fun StringBuilder.appendSections(): StringBuilder =
    buildSections().let { sections ->
      if (sections.isNotEmpty()) {
        append(DocumentationMarkup.SECTIONS_START)
          .append('\n')
        sections.entries.forEach { (name, value) ->
          append(DocumentationMarkup.SECTION_HEADER_START)
            .append(StringUtil.capitalize(name))
          if (value.isNotBlank()) {
            if (!name.endsWith(":"))
              append(':')
            append(DocumentationMarkup.SECTION_SEPARATOR)
              .append(value)
          }
          append(DocumentationMarkup.SECTION_END)
            .append('\n')
        }
        append(DocumentationMarkup.SECTIONS_END)
          .append('\n')
      }
      this
    }

  private fun StringBuilder.appendFootnote(): StringBuilder =
    footnote?.let {
      append(DocumentationMarkup.CONTENT_START)
        .append(it)
        .append(DocumentationMarkup.CONTENT_END)
        .append('\n')
    } ?: this

  private fun StringBuilder.appendHeader(): StringBuilder =
    header?.let {
      append("<div class='" + DocumentationMarkup.CLASS_TOP + "'>")
        .append(it)
        .append("</div>\n")
    } ?: this

  private fun buildSections(): Map<String, String> =
    LinkedHashMap(descriptionSections).also { sections ->
      apiStatus?.let { status ->
        when (status) {
          is PolySymbolApiStatus.Deprecated -> {
            sections[PolySymbolsBundle.message("mdn.documentation.section.status.Deprecated")] = status.message ?: ""
            status.since?.let { sections[PolySymbolsBundle.message("mdn.documentation.section.status.DeprecatedSince")] = it }
          }
          is PolySymbolApiStatus.Obsolete -> {
            sections[PolySymbolsBundle.message("mdn.documentation.section.status.Obsolete")] = status.message ?: ""
            status.since?.let { sections[PolySymbolsBundle.message("mdn.documentation.section.status.ObsoleteSince")] = it }
          }
          is PolySymbolApiStatus.Experimental -> {
            sections[PolySymbolsBundle.message("mdn.documentation.section.status.Experimental")] = status.message ?: ""
            status.since?.let { sections[PolySymbolsBundle.message("mdn.documentation.section.status.Since")] = it }
          }
          is PolySymbolApiStatus.Stable -> {
            status.since?.let { sections[PolySymbolsBundle.message("mdn.documentation.section.status.Since")] = it }
          }
        }
      }
      defaultValue?.let { sections[PolySymbolsBundle.message("mdn.documentation.section.defaultValue")] = "<p><code>$it</code>" }
      library?.let { sections[PolySymbolsBundle.message("mdn.documentation.section.library")] = "<p>$it" }
    }

  private fun StringBuilder.appendIcon(icon: Icon, url2ImageMap: MutableMap<String, Image>): StringBuilder {
    // TODO adjust it to the actual component being used
    @Suppress("UndesirableClassUsage")
    val bufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val g = bufferedImage.createGraphics()
    g.font = UIUtil.getToolTipFont()
    val height = (g.fontMetrics.getStringBounds("a", g).height / ScaleContext.create().getScale(ScaleType.USR_SCALE)).toInt()
    g.dispose()
    val image = try {
      IconUtil.toBufferedImage(icon.scaleToHeight(height))
    }
    catch (e: Exception) {
      // ignore
      return this
    }
    val url = "https://img${url2ImageMap.size}"
    url2ImageMap[url] = image
    val screenHeight = height * ScaleContext.create().getScale(ScaleType.SYS_SCALE)
    append("<img src='$url' height=\"$screenHeight\" width=\"${(screenHeight * icon.iconWidth) / icon.iconHeight}\" border=0 />")
    return this
  }

  private val imgSrcRegex = Regex("<img [^>]*src\\s*=\\s*['\"]([^'\"]+)['\"]")

  private fun String.loadLocalImages(origin: PolySymbolOrigin, url2ImageMap: MutableMap<String, Image>): String {
    val replaces = imgSrcRegex.findAll(this)
      .mapNotNull { it.groups[1] }
      .filter { !it.value.contains(':') }
      .mapNotNull { group ->
        origin.loadIcon(group.value)
          ?.let { IconUtil.toBufferedImage(it, true) }
          ?.let {
            val url = "https://img${url2ImageMap.size}"
            url2ImageMap[url] = it
            Pair(group.range, url)
          }
      }
      .sortedBy { it.first.first }
      .toList()
    if (replaces.isEmpty()) return this
    val result = StringBuilder()
    var lastIndex = 0
    for (replace in replaces) {
      result.appendRange(this, lastIndex, replace.first.first)
      result.append(replace.second)
      lastIndex = replace.first.last + 1
    }
    if (lastIndex < this.length) {
      result.appendRange(this, lastIndex, this.length)
    }
    return result.toString()
  }

}