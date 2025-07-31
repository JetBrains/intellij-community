// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.testFramework.query

import com.intellij.polySymbols.testFramework.DebugOutputPrinter
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
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
    throw FileComparisonFailedError(testName, expected, result, expectedFile.toString())
  }
}

fun printCodeCompletionItems(items: List<PolySymbolCodeCompletionItem>,
                             printer: DebugOutputPrinter = PolySymbolsDebugOutputPrinter()): String =
  printer.printValue(
    items.sortedWith(Comparator
                       .comparing<PolySymbolCodeCompletionItem, PolySymbol.Priority> {
                         it.priority ?: PolySymbol.Priority.NORMAL
                       }
                       .thenComparing(Function { it.name })
                       .thenComparing(Function { it.offset })))

fun printMatches(matches: List<PolySymbol>, printer: DebugOutputPrinter = PolySymbolsDebugOutputPrinter()): String =
  printer.printValue(
    matches.sortedWith(
      compareBy<PolySymbol> { it.priority ?: PolySymbol.Priority.NORMAL }
        .thenBy { it.name }
        .thenBy { it.origin.library ?: "" }
    )
  )