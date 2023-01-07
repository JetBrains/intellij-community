// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation.impl

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.documentation.WebSymbolDocumentationTarget
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.webSymbols.impl.scaleToHeight
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.Icon

internal class WebSymbolDocumentationTargetImpl(override val symbol: WebSymbol) : WebSymbolDocumentationTarget {

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val pointer = symbol.createPointer()
    return Pointer<DocumentationTarget> {
      pointer.dereference()?.let { WebSymbolDocumentationTargetImpl(it) }
    }
  }

  companion object {
    fun buildDocumentation(doc: WebSymbolDocumentation): DocumentationResult? {
      val url2ImageMap = mutableMapOf<String, Image>()

      @Suppress("HardCodedStringLiteral")
      val contents = StringBuilder()
        .appendDefinition(doc, url2ImageMap)
        .appendDescription(doc)
        .appendSections(doc)
        .appendFootnote(doc)
        .toString()
      return DocumentationResult.documentation(contents).images(url2ImageMap).externalUrl(doc.docUrl)
    }


    private fun StringBuilder.appendDefinition(doc: WebSymbolDocumentation, url2ImageMap: MutableMap<String, Image>): StringBuilder =
      append(DocumentationMarkup.DEFINITION_START)
        .also {
          doc.icon?.let { appendIcon(it, url2ImageMap).append("&nbsp;&nbsp;") }
        }
        .append(doc.definition)
        .append(DocumentationMarkup.DEFINITION_END)
        .append('\n')

    private fun StringBuilder.appendDescription(doc: WebSymbolDocumentation): StringBuilder =
      doc.description?.let {
        append(DocumentationMarkup.CONTENT_START).append('\n')
          .append(it).append('\n')
          .append(DocumentationMarkup.CONTENT_END)
      }
      ?: this

    private fun StringBuilder.appendSections(doc: WebSymbolDocumentation): StringBuilder =
      buildSections(doc).let { sections ->
        if (sections.isNotEmpty()) {
          append(DocumentationMarkup.SECTIONS_START)
            .append('\n')
          sections.entries.forEach { (name, value) ->
            append(DocumentationMarkup.SECTION_HEADER_START)
              .append(StringUtil.capitalize(name))
            if (value.isNotBlank()) {
              if (!name.endsWith(":"))
                append(':')
              // Workaround misalignment of monospace font
              if (value.contains("<code")) {
                append("<code> </code>")
              }
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

    private fun StringBuilder.appendFootnote(doc: WebSymbolDocumentation): StringBuilder =
      doc.footnote?.let {
        append(DocumentationMarkup.CONTENT_START)
          .append(it)
          .append(DocumentationMarkup.CONTENT_END)
          .append('\n')
      } ?: this

    private fun buildSections(doc: WebSymbolDocumentation): Map<String, String> =
      LinkedHashMap(doc.descriptionSections).also { sections ->
        if (doc.required) sections[WebSymbolsBundle.message("mdn.documentation.section.isRequired")] = ""
        if (doc.deprecated) sections[WebSymbolsBundle.message("mdn.documentation.section.status.Deprecated")] = ""
        if (doc.experimental) sections[WebSymbolsBundle.message("mdn.documentation.section.status.Experimental")] = ""
        doc.defaultValue?.let { sections[WebSymbolsBundle.message("mdn.documentation.section.defaultValue")] = "<p><code>$it</code>" }
        doc.library?.let { sections[WebSymbolsBundle.message("mdn.documentation.section.library")] = "<p>$it" }
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
  }

}