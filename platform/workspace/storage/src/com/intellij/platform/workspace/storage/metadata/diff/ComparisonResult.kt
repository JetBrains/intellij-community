// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.diff

internal sealed interface ComparisonResult {
  val areEquals: Boolean

  val info: String
}

internal object Equal: ComparisonResult {
  override val areEquals: Boolean
    get() = true
  override val info: String
    get() = "Cache and existing version of entities are equal"

  override fun toString(): String = "equal"
}

internal class NotEqual(override val info: String): ComparisonResult {
  override val areEquals: Boolean
    get() = false
}

internal class NotEqualWithLog: ComparisonResult {
  val log: NotEqualEntitiesLog = NotEqualEntitiesLog()

  override val areEquals: Boolean
    get() = false
  override val info: String
    get() = log.printLog()

  override fun toString(): String = "not equal"
}



internal class NotEqualEntitiesLog: MetadataComparatorLog {
  private val logLines: ArrayDeque<LogLine> = ArrayDeque()
  private val indent: String = "  "

  override fun startComparing(toCompare: String) {
    logLines.addFirst(LogLine("Start comparing $toCompare", levelAfter = 1))
  }

  override fun comparisonResult(output: String, comparisonResult: ComparisonResult) {
    logLines.addLast(LogLine("$output $indent Result: $comparisonResult"))
  }

  override fun endComparing(isCompared: String, comparisonResult: ComparisonResult) {
    logLines.addLast(LogLine("End comparing $isCompared $indent Result: $comparisonResult", levelBefore = -1))
  }

  override fun ignoreComparing(isIgnored: String) {
    logLines.addLast(LogLine("$isIgnored was not compared"))
  }

  override fun printLog(): String {
    val sb = StringBuilder()
    var currentLevel = 0

    logLines.forEach { line ->
      currentLevel += line.levelBefore
      sb.appendLine("${getIndent(currentLevel)}${line.string}")
      currentLevel += line.levelAfter
    }

    return sb.toString()
  }

  private data class LogLine(val string: String, val levelBefore: Int = 0, val levelAfter: Int = 0)

  private fun getIndent(indentLevel: Int) = indent.repeat(indentLevel)
}