// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.DebugOutputPrinter
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.utils.completeMatch
import java.util.*

open class WebSymbolsDebugOutputPrinter : DebugOutputPrinter() {

  private val parents = Stack<WebSymbol>()

  override fun printValueImpl(builder: StringBuilder, level: Int, value: Any?): StringBuilder =
    when (value) {
      is WebSymbolCodeCompletionItem -> builder.printCodeCompletionItem(level, value)
      is WebSymbol -> builder.printSymbol(level, value)
      is WebSymbolHtmlAttributeValue -> builder.printAttributeValue(level, value)
      is WebSymbolNameSegment -> builder.printSegment(level, value)
      else -> super.printValueImpl(builder, level, value)
    }

  override fun printRecursiveValue(builder: StringBuilder, level: Int, value: Any): StringBuilder =
    if (value is WebSymbol)
      if (parents.peek() == value) builder.append("<self>") else builder.append("<recursive>")
    else
      super.printRecursiveValue(builder, level, value)

  private fun StringBuilder.printCodeCompletionItem(topLevel: Int, item: WebSymbolCodeCompletionItem): StringBuilder =
    printObject(topLevel) { level ->
      printProperty(level, "name", item.name)
      printProperty(level, "priority", item.priority ?: WebSymbol.Priority.NORMAL)
      printProperty(level, "proximity", item.proximity?.takeIf { it > 0 })
      printProperty(level, "displayName", item.displayName.takeIf { it != item.name })
      printProperty(level, "offset", item.offset.takeIf { it != 0 })
      printProperty(level, "completeAfterInsert", item.completeAfterInsert.takeIf { it })
      printProperty(level, "completeAfterChars", item.completeAfterChars.takeIf { it.isNotEmpty() })
      printProperty(level, "aliases", item.aliases.takeIf { it.isNotEmpty() })
      printProperty(level, "source", item.symbol)
    }


  private fun StringBuilder.printSymbol(topLevel: Int, source: WebSymbol): StringBuilder {
    if (parents.contains(source)) {
      if (parents.peek() == source) append("<self>") else append("<recursive>")
      return this
    }
    printObject(topLevel) { level ->
      if (source.pattern != null) {
        printProperty(level, "matchedName", source.namespace.lowercase(Locale.US) + "/" + source.kind + "/<pattern>")
        printProperty(level, "name", source.name)
      } else {
        printProperty(level, "matchedName", source.namespace.lowercase(Locale.US) + "/" + source.kind + "/" + source.name)
      }
      printProperty(level, "origin", "${source.origin.library}@${source.origin.version} (${source.origin.framework ?: "<none>"})")
      printProperty(level, "source", (source as? PsiSourcedWebSymbol)?.source)
      printProperty(level, "type", source.type)
      printProperty(level, "attrValue", source.attributeValue)
      printProperty(level, "complete", source.completeMatch)
      printProperty(level, "description", source.description?.ellipsis(45))
      printProperty(level, "docUrl", source.docUrl)
      printProperty(level, "descriptionSections", source.descriptionSections.takeIf { it.isNotEmpty() })
      printProperty(level, "abstract", source.abstract.takeIf { it })
      printProperty(level, "virtual", source.virtual.takeIf { it })
      printProperty(level, "deprecated", source.deprecated.takeIf { it })
      printProperty(level, "experimental", source.experimental.takeIf { it })
      printProperty(level, "priority", source.priority ?: WebSymbol.Priority.NORMAL)
      printProperty(level, "proximity", source.proximity?.takeIf { it > 0 })
      printProperty(level, "has-pattern", if (source.pattern != null) true else null)
      printProperty(level, "properties", source.properties.takeIf { it.isNotEmpty() })
      parents.push(source)
      printProperty(level, "segments", source.nameSegments)
      parents.pop()
    }
    return this
  }


  private fun StringBuilder.printSegment(topLevel: Int,
                                         segment: WebSymbolNameSegment): StringBuilder =
    printObject(topLevel) { level ->
      printProperty(level, "name-part", segment.getName(parents.peek()))
      printProperty(level, "display-name", segment.displayName)
      printProperty(level, "deprecated", segment.deprecated.takeIf { it })
      printProperty(level, "priority", segment.priority?.takeIf { it != WebSymbol.Priority.NORMAL })
      printProperty(level, "matchScore", segment.matchScore.takeIf { it != segment.end - segment.start })
      printProperty(level, "problem", segment.problem)
      val symbols = segment.symbols.filter { !it.extension }
      if (symbols.size > 1) {
        printProperty(level, "symbols", symbols)
      }
      else if (symbols.size == 1) {
        printProperty(level, "symbol", symbols[0])
      }
    }

  private fun StringBuilder.printAttributeValue(topLevel: Int, value: WebSymbolHtmlAttributeValue): StringBuilder =
    printObject(topLevel) { level ->
      printProperty(level, "kind", value.kind)
        .printProperty(level, "type", value.type)
        .printProperty(level, "langType", value.langType)
        .printProperty(level, "required", value.required)
        .printProperty(level, "default", value.default)
    }

}