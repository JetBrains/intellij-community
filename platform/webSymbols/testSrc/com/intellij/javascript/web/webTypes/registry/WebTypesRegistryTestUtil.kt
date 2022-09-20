package com.intellij.javascript.web.webTypes.registry

import com.intellij.javascript.web.symbols.PsiSourcedWebSymbol
import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbol.NameSegment
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItem
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import java.io.File
import java.util.*
import java.util.function.Function

fun UsefulTestCase.doTest(testPath: String, test: () -> String) {
  val result = test().replace("\r\n", "\n").trim()
  val testName = PlatformTestUtil.getTestName(name, true)
  val expectedFile = File(testPath, "$testName.gold.txt")
  if (!expectedFile.exists()) {
    expectedFile.createNewFile()
  }
  val expected = FileUtil.loadFile(expectedFile)
    .replace("\r\n", "\n")
    .trim()
  if (result != expected) {
    throw FileComparisonFailure(testName, expected, result, expectedFile.toString())
  }
}

fun printCodeCompletionItems(items: List<WebSymbolCodeCompletionItem>): String {
  val result = StringBuilder()
    .append("[\n")
  items
    .sortedWith(Comparator
                  .comparing<WebSymbolCodeCompletionItem, WebSymbol.Priority> { it.priority ?: WebSymbol.Priority.NORMAL }
                  .thenComparing(Function { it.name })
                  .thenComparing(Function { it.offset }))
    .forEach { match ->
      result.indent(1).append("{\n")
        .printCodeCompletionItem(2, match)
        .indent(1).append("},\n")
    }
  return result.append("]\n").toString()
}

fun printMatches(matches: List<WebSymbol>): String {
  val result = StringBuilder()
    .append("[\n")
  matches
    .sortedWith(Comparator
                  .comparing<WebSymbol, WebSymbol.Priority?> { it.priority ?: WebSymbol.Priority.NORMAL }
                  .thenComparing(Function { it.matchedName })
                  .thenComparing(Function { it.name })
                  .thenComparing(Function { it.origin.packageName ?: "" }))
    .forEach { match ->
      result.indent(1).append("{\n")
        .printSymbol(2, match)
        .indent(1).append("},\n")
    }
  return result.append("]\n").toString()
}

private fun StringBuilder.indent(level: Int): StringBuilder =
  append(" ".repeat(level))

private fun StringBuilder.printCodeCompletionItem(level: Int, item: WebSymbolCodeCompletionItem): StringBuilder {
  printProperty(level, "name", item.name)
  printProperty(level, "priority", item.priority ?: WebSymbol.Priority.NORMAL)
  printProperty(level, "proximity", item.proximity?.takeIf { it > 0 })
  printProperty(level, "displayName", item.displayName.takeIf { it != item.name })
  printProperty(level, "offset", item.offset.takeIf { it != 0 })
  printProperty(level, "completeAfterInsert", item.completeAfterInsert.takeIf { it })
  printProperty(level, "completeAfterChars", item.completeAfterChars.takeIf { it.isNotEmpty() })
  printProperty(level, "aliases", item.aliases.takeIf { it.isNotEmpty() })
  if (item.symbol != null) {
    indent(level).append("source: {\n")
      .printSymbol(level + 1, item.symbol!!)
      .indent(level).append("},\n")
  }
  return this
}

private fun String.ellipsis(maxLength: Int): String =
  substring(0, length.coerceAtMost(maxLength)) + if (length > maxLength) "…" else ""

private fun StringBuilder.printSymbol(level: Int, source: WebSymbol): StringBuilder {
  printProperty(level, "matchedName", source.namespace.name.lowercase(Locale.US) + "/" + source.kind + "/" + source.matchedName)
  printProperty(level, "name", source.name.takeIf { it != source.matchedName })
  printProperty(level, "origin", "${source.origin.packageName}@${source.origin.version} (${source.origin.framework ?: "<none>"})")
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
  indent(level).append("segments: [\n")
  source.nameSegments.forEach { segment ->
    indent(level + 1).append("{\n")
    printSegment(level + 2, source, segment)
    indent(level + 1).append("},\n")
  }
  indent(level).append("],\n")
  return this
}

private fun StringBuilder.printMap(level: Int,
                                   map: Map<*, *>): StringBuilder {
  append("{\n")
  for (entry in map) {
    indent(level + 1).append(entry.key).append(": ")
      .printValue(level +1, entry.value)
      //.append(entry.value.toString().replace("\n", "\\n").ellipsis(45))
      .append("\n")
  }
  indent(level).append("}")
  return this
}

private fun StringBuilder.printList(level: Int, list: List<*>): StringBuilder {
  append("[")
  if (list.isEmpty()) {
    append("],\n")
  }
  else {
    append('\n')
    list.forEach {
      indent(level+1).printValue(level + 1, it).append(",\n")
    }
    indent(level).append("]")
  }
  return this
}

private fun StringBuilder.printSegment(level: Int,
                                       parent: WebSymbol,
                                       segment: NameSegment): StringBuilder {
  printProperty(level, "name-part", parent.matchedName.substring(segment.start, segment.end))
  printProperty(level, "display-name", segment.displayName)
  printProperty(level, "deprecated", segment.deprecated.takeIf { it })
  printProperty(level, "priority", segment.priority?.takeIf { it != WebSymbol.Priority.NORMAL })
  printProperty(level, "matchScore", segment.matchScore.takeIf { it != segment.end - segment.start })
  printProperty(level, "problem", segment.problem)
  val symbols = segment.symbols.filter { !it.extension }
  if (symbols.size > 1) {
    indent(level).append("symbols: [\n")
    printSymbols(level + 1, symbols, parent)
    indent(level).append("]\n")
  }
  else if (symbols.size == 1) {
    when (val symbol = symbols[0]) {
      parent -> indent(level).append("symbol: <self>,\n")
      else -> indent(level).append("symbol: {\n")
        .printSymbol(level + 1, symbol)
        .indent(level).append("},\n")
    }
  }
  return this
}

private fun StringBuilder.printSymbols(level: Int,
                                       symbols: List<WebSymbol>,
                                       parent: WebSymbol): StringBuilder {
  symbols.forEach { symbol ->
    when (symbol) {
      parent -> indent(level).append("<self>,\n")
      else -> indent(level).append("{\n")
        .printSymbol(level + 1, symbol)
        .indent(level).append("},\n")
    }
  }
  return this
}

private fun StringBuilder.printProperty(level: Int, name: String, value: Any?): StringBuilder {
  if (value == null) return this
  indent(level).append(name).append(": ")
    .printValue(level, value)
    .append(",\n")
  return this
}

private fun StringBuilder.printValue(level: Int, value: Any?): StringBuilder =
  when (value) {
    is String -> append("\"").append(value.ellipsis(80)).append("\"")
    is PsiElement -> printPsiElement(value)
    is List<*> -> printList(level, value)
    is Map<*, *> -> printMap(level, value)
    is WebSymbol.AttributeValue -> append("{\n")
      .printAttributeValue(level + 1, value)
      .indent(level)
      .append("}")
    null -> append("<null>")
    else -> append(value)
  }

private fun StringBuilder.printPsiElement(element: PsiElement): StringBuilder {
  append(element::class.java.simpleName)
    .append(" <")
    .append(element.containingFile.virtualFile?.path)
  if (element !is PsiFile) append(": " + element.textRange)
  return append(">")
}

private fun StringBuilder.printAttributeValue(level: Int, value: WebSymbol.AttributeValue): StringBuilder =
  printProperty(level, "kind", value.kind)
    .printProperty(level, "type", value.type)
    .printProperty(level, "langType", value.langType)
    .printProperty(level, "required", value.required)
    .printProperty(level, "default", value.default)