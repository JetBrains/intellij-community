package com.intellij.javascript.web.webTypes.registry

import com.intellij.javascript.web.DebugOutputPrinter
import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItem
import com.intellij.openapi.util.io.FileUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import java.io.File
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

fun printCodeCompletionItems(items: List<WebSymbolCodeCompletionItem>,
                             printer: DebugOutputPrinter = WebSymbolsDebugOutputPrinter()): String =
  printer.printValue(
    items.sortedWith(Comparator
                       .comparing<WebSymbolCodeCompletionItem, WebSymbol.Priority> {
                         it.priority ?: WebSymbol.Priority.NORMAL
                       }
                       .thenComparing(Function { it.name })
                       .thenComparing(Function { it.offset })))

fun printMatches(matches: List<WebSymbol>, printer: DebugOutputPrinter = WebSymbolsDebugOutputPrinter()): String =
  printer.printValue(
    matches
      .sortedWith(Comparator
                    .comparing<WebSymbol, WebSymbol.Priority?> { it.priority ?: WebSymbol.Priority.NORMAL }
                    .thenComparing(Function { it.matchedName })
                    .thenComparing(Function { it.name })
                    .thenComparing(Function { it.origin.packageName ?: "" })))