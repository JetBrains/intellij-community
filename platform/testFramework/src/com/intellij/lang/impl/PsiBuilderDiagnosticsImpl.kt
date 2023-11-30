// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl

import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.streams.asSequence

class PsiBuilderDiagnosticsImpl(private val collectTraces: Boolean = false, ignoreMatching: Set<String> = emptySet()) : PsiBuilderDiagnostics {
  private val rollbacks: MutableMap<Int, AtomicInteger> = hashMapOf()
  private val passes: MutableList<Pair<Int, Int>> = mutableListOf()
  private val traces: MutableMap<StackTraceElement, StatEntry> = hashMapOf()
  private val ignoreLines: Pattern = (ignoreMatching + listOf(
    Thread::class,
    PsiBuilderDiagnosticsImpl::class,
    PsiBuilderImpl::class,
  )
    .mapNotNull { cls -> cls.qualifiedName })
    .joinToString("|") { chunk -> Pattern.quote(chunk) }
    .let { pattern -> Pattern.compile(pattern) }

  override fun toString(): String {
    var totalRollbacks = 0
    var totalRolledback = 0
    var nonEmptyRollbacks = 0
    rollbacks.forEach { (length, rollbacksRef) ->
      val rollbacks = rollbacksRef.get()
      totalRollbacks += rollbacks
      totalRolledback += rollbacks * length
      if (length > 0) {
        nonEmptyRollbacks += rollbacks
      }
    }
    var lexemeCount = 0
    var builders = 0
    val passes = this.passes.joinToString("\n") {
      builders++
      lexemeCount += it.second
      "${it.first};${it.second}"
    }

    val rollbacks = rollbacks.entries.asSequence()
      .sortedBy { it.key }
      .joinToString("\n") { (length, rollbacksRef) ->
        val rollbacks = rollbacksRef.get()
        val tokensRolled = length * rollbacks
        listOf(length, rollbacks, tokensRolled,
               percent(tokensRolled, lexemeCount),
               percent(rollbacks, totalRollbacks),
               percent(tokensRolled, totalRolledback)).joinToString(";")
      }

    val rollbackSources = traces.entries.asSequence()
      .sortedBy { (_, statEntry) -> -statEntry.tokens }
      .joinToString("\n") { (stackFrame, statEntry) ->
        val (entryRollbacks, entryTokens, entryNonEmptyRollbacks, entryMaxRollback) = statEntry
        val entryAvgRollback = avg(entryTokens, entryNonEmptyRollbacks)
        val entryPercentRolled = percent(entryTokens, totalRolledback)
        val invocationPoint = stackFrame.toString()
          .replace(Regex("^((?:\\w+\\.(?=\\w+\\.))++)")) { match ->
            match.groupValues[0].split(".").joinToString(".") { chunk -> if (chunk.isEmpty()) "" else chunk[0].toString() }
          }
        "$invocationPoint;$entryRollbacks;$entryNonEmptyRollbacks;$entryTokens;$entryPercentRolled;$entryAvgRollback;$entryMaxRollback"
      }

    return """Summary:

                Passes: $builders
          Tokens count: $lexemeCount
       Total rollbacks: $totalRollbacks
   Non-empty rollbacks: $nonEmptyRollbacks
           Rolled back: $totalRolledback (${percent(totalRolledback, lexemeCount)}) tokens
AVG non-empty rollback: ${avg(totalRolledback, nonEmptyRollbacks)}

Passes CSV data:

Characters;Tokens
$passes

Rollbacks CSV data:

Tokens;Rollbacks;Tokens rolled;% of tokens count;% of rollbacks;% of rolled back
$rollbacks
        """.trimIndent() + if (rollbackSources.isEmpty()) ""
    else """

Rollback traces CSV stat:

Invocation point;Rollbacks;Non-Empty Rollbacks;Rolled Tokens;% of total rolled;AVG Non-Empty Rollback;MAX Rollback
$rollbackSources
        """
  }

  /**
   * @return formatted average of [amount]/[count]
   */
  private fun avg(amount: Int, count: Int) =
    if (count == 0) "" else String.format(Locale.US, "%.03f", amount.toFloat() / count)

  /**
   * @return formatted percentage of one [part] in relation to [whole].
   */
  private fun percent(part: Int, whole: Int) =
    if (whole == 0) "" else String.format(Locale.US, "%.02f%%", part.toFloat() * 100 / whole)

  override fun registerPass(charLength: Int, tokensLength: Int) {
    passes += charLength to tokensLength
  }

  override fun registerRollback(tokens: Int) {
    rollbacks.computeIfAbsent(tokens, { AtomicInteger() }).incrementAndGet()

    if (collectTraces) {
      StackWalker.getInstance().walk { framesStream ->
        framesStream.asSequence()
          .map { walkerFrame -> walkerFrame.toStackTraceElement() }
          .find { stackFrame -> !(ignoreLines.matcher(stackFrame.toString()).find()) }
          ?.let { stackFrame -> this.traces.computeIfAbsent(stackFrame, { StatEntry() }).registerRollback(tokens) }
      }
    }
  }

  private data class StatEntry(var rollbacks: Int = 0,
                               var tokens: Int = 0,
                               var nonEmptyRollbacks: Int = 0,
                               var maxTokens: Int = 0) {
    fun registerRollback(tokens: Int) {
      rollbacks++
      this.tokens += tokens
      maxTokens = max(maxTokens, tokens)
      if (tokens > 0) {
        nonEmptyRollbacks++
      }
    }
  }

  companion object {
    @JvmStatic
    fun <T> runWithDiagnostics(diagnostics: PsiBuilderDiagnostics?, computable: Supplier<T>): T {
      val oldValue = PsiBuilderImpl.DIAGNOSTICS
      try {
        PsiBuilderImpl.DIAGNOSTICS = diagnostics
        return computable.get()
      }
      finally {
        PsiBuilderImpl.DIAGNOSTICS = oldValue
      }
    }
  }
}