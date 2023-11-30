// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.diff

import java.lang.StringBuilder

internal interface MetadataComparatorLog {

  fun startComparing(toCompare: String)

  fun comparisonResult(output: String, comparisonResult: ComparisonResult)

  fun endComparing(isCompared: String, comparisonResult: ComparisonResult)

  fun ignoreComparing(isIgnored: String)

  fun printLog(): String
}


internal class EntitiesComparatorLog private constructor(): MetadataComparatorLog {
  private val log: StringBuilder = StringBuilder()
  private val indent: String = "  "
  private var indentLevel = 0


  companion object {
    var INSTANCE: EntitiesComparatorLog = EntitiesComparatorLog()
      private set

    fun newInstance(): EntitiesComparatorLog {
      INSTANCE = EntitiesComparatorLog()
      return INSTANCE
    }
  }

  override fun startComparing(toCompare: String) {
    line("Start comparing $toCompare")
    indentLevel++
  }

  override fun comparisonResult(output: String, comparisonResult: ComparisonResult) {
    line("$output $indent Result: $comparisonResult")
  }

  override fun endComparing(isCompared: String, comparisonResult: ComparisonResult) {
    indentLevel--
    line("End comparing $isCompared $indent Result: $comparisonResult")
  }

  override fun ignoreComparing(isIgnored: String) {
    line("$isIgnored was not compared")
  }

  private fun line(string: String) {
    log.appendLine("${getIndent()}$string")
  }

  override fun printLog() = log.toString()

  private fun getIndent() = indent.repeat(indentLevel)
}
