// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.testFramework.query

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbol.DocHidePatternProperty
import com.intellij.polySymbols.PolySymbol.HideFromCompletionProperty
import com.intellij.polySymbols.PolySymbol.InjectLanguageProperty
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.css.PROP_CSS_ARGUMENTS
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.html.htmlAttributeValue
import com.intellij.polySymbols.js.JsSymbolKindProperty
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.testFramework.DebugOutputPrinter
import com.intellij.polySymbols.utils.PolySymbolTypeSupport.TypeSupportProperty
import com.intellij.polySymbols.utils.completeMatch
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.polySymbols.utils.qualifiedName
import com.intellij.polySymbols.utils.unwrapMatchedSymbols
import com.intellij.polySymbols.webTypes.WebTypesSymbol
import com.intellij.util.applyIf
import com.intellij.util.asSafely
import java.util.Stack

open class PolySymbolsDebugOutputPrinter : DebugOutputPrinter() {

  private val parents = Stack<PolySymbol>()

  protected open val propertiesToPrint: List<PolySymbolProperty<*>> =
    listOf(
      HideFromCompletionProperty, DocHidePatternProperty, InjectLanguageProperty,
      PROP_CSS_ARGUMENTS, JsSymbolKindProperty, WebTypesSymbol.PROP_NO_DOC,
    )

  override fun printValueImpl(builder: StringBuilder, level: Int, value: Any?): StringBuilder =
    when (value) {
      is PolySymbolCodeCompletionItem -> builder.printCodeCompletionItem(level, value)
      is PolySymbol -> builder.printSymbol(level, value)
      is PolySymbolHtmlAttributeValue -> builder.printAttributeValue(level, value)
      is PolySymbolNameSegment -> builder.printSegment(level, value)
      is PolySymbolApiStatus -> builder.printApiStatus(value)
      is Set<*> -> builder.printSet(value)
      else -> super.printValueImpl(builder, level, value)
    }

  override fun printRecursiveValue(builder: StringBuilder, level: Int, value: Any): StringBuilder =
    if (value is PolySymbol)
      if (parents.peek() == value) builder.append("<self>") else builder.append("<recursive>")
    else
      super.printRecursiveValue(builder, level, value)

  private fun StringBuilder.printCodeCompletionItem(topLevel: Int, item: PolySymbolCodeCompletionItem): StringBuilder =
    printObject(topLevel) { level ->
      printProperty(level, "name", item.name)
      printProperty(level, "priority", item.priority ?: PolySymbol.Priority.NORMAL)
      printProperty(level, "proximity", item.proximity?.takeIf { it > 0 })
      printProperty(level, "displayName", item.displayName.takeIf { it != item.name })
      printProperty(level, "offset", item.offset.takeIf { it != 0 })
      printProperty(level, "completeAfterInsert", item.completeAfterInsert.takeIf { it })
      printProperty(level, "completeAfterChars", item.completeAfterChars.takeIf { it.isNotEmpty() })
      printProperty(level, "aliases", item.aliases.takeIf { it.isNotEmpty() })
      printProperty(level, "source", item.symbol)
    }

  private fun StringBuilder.printSet(set: Set<*>): StringBuilder {
    return append(set.toString())
  }

  private fun StringBuilder.printSymbol(topLevel: Int, source: PolySymbol): StringBuilder {
    if (parents.contains(source)) {
      if (parents.peek() == source) append("<self>") else append("<recursive>")
      return this
    }
    printObject(topLevel) { level ->
      if (source is PolySymbolWithPattern) {
        printProperty(level, "matchedName", source.kind.toString() + "/<pattern>")
        printProperty(level, "name", source.name)
      }
      else {
        printProperty(level, "matchedName", source.qualifiedName.toString())
      }

      val documentation = source.getDocumentationTarget(null)
        .asSafely<PolySymbolDocumentationTarget>()
        ?.documentation

      val framework = (source as? WebTypesSymbol)?.origin?.framework
                      ?: (source as? PolySymbolMatch)?.unwrapMatchedSymbols()
                        ?.firstNotNullOfOrNull { (it as? WebTypesSymbol)?.origin?.framework }
                      ?: "<none>"
      printProperty(level,
                    "origin",
                    "${documentation?.library} ($framework)")
      printProperty(level, "source", (source as? PsiSourcedPolySymbol)?.source)
      printProperty(level, "type", source[TypeSupportProperty]?.typeProperty?.let { source[it] })
      printProperty(level, "attrValue", source.htmlAttributeValue)
      printProperty(level, "complete", source.completeMatch)
      if (documentation != null && source !is PolySymbolMatch) {
        printProperty(level, "description", documentation.description?.ellipsis(45))
        printProperty(level, "docUrl", documentation.docUrl)
        printProperty(level, "descriptionSections", documentation.descriptionSections.takeIf { it.isNotEmpty() })
      }
      printProperty(level, "modifiers", source.modifiers.takeIf { it.isNotEmpty() }
        ?.toSortedSet { a, b -> a.name.compareTo(b.name) })
      printProperty(level, "apiStatus", source.apiStatus.takeIf { it !is PolySymbolApiStatus.Stable || it.since != null })
      printProperty(level, "priority", source.priority ?: PolySymbol.Priority.NORMAL)
      printProperty(level, "has-pattern", if (source is PolySymbolWithPattern) true else null)
      printProperty(
        level, "properties",
        propertiesToPrint
          .sortedBy { it.name }
          .mapNotNull { prop -> source[prop]?.let { Pair(prop.name, it) } }
          .toMap()
          .takeIf { it.isNotEmpty() }
      )
      parents.push(source)
      printProperty(level, "segments", source.nameSegments)
      parents.pop()
    }
    return this
  }


  private fun StringBuilder.printSegment(
    topLevel: Int,
    segment: PolySymbolNameSegment,
  ): StringBuilder =
    printObject(topLevel) { level ->
      printProperty(level, "name-part", parents.peek().let {
        if (it !is PolySymbolWithPattern) segment.getName(parents.peek()) else ""
      })
      printProperty(level, "display-name", segment.displayName)
      printProperty(level, "apiStatus", segment.apiStatus)
      printProperty(level, "priority", segment.priority?.takeIf { it != PolySymbol.Priority.NORMAL })
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

  private fun StringBuilder.printAttributeValue(topLevel: Int, value: PolySymbolHtmlAttributeValue): StringBuilder =
    printObject(topLevel) { level ->
      printProperty(level, "kind", value.kind)
        .printProperty(level, "type", value.type)
        .printProperty(level, "langType", value.langType)
        .printProperty(level, "required", value.required)
        .printProperty(level, "default", value.default)
    }

  private fun StringBuilder.printApiStatus(apiStatus: PolySymbolApiStatus): StringBuilder =
    when (apiStatus) {
      is PolySymbolApiStatus.Deprecated -> append("deprecated")
        .applyIf(apiStatus.since != null) { append(" in ").append(apiStatus.since) }
        .applyIf(apiStatus.message != null) { append(" (").append(apiStatus.message).append(")") }
      is PolySymbolApiStatus.Obsolete -> append("obsolete")
        .applyIf(apiStatus.since != null) { append(" in ").append(apiStatus.since) }
        .applyIf(apiStatus.message != null) { append(" (").append(apiStatus.message).append(")") }
      is PolySymbolApiStatus.Experimental -> append("experimental")
        .applyIf(apiStatus.since != null) { append(" since ").append(apiStatus.since) }
        .applyIf(apiStatus.message != null) { append(" (").append(apiStatus.message).append(")") }
      is PolySymbolApiStatus.Stable -> append("stable")
        .applyIf(apiStatus.since != null) { append(" since ").append(apiStatus.since) }
    }

}