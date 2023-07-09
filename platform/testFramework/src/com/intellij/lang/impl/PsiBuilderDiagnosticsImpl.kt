// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import java.util.regex.Pattern
import kotlin.streams.asSequence

class PsiBuilderDiagnosticsImpl(val collectTraces: Boolean = false, ignoreMatching: Set<String> = emptySet()) : PsiBuilderDiagnostics {
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
    rollbacks.forEach {
      totalRollbacks += it.value.get()
      totalRolledback += it.key * it.value.get()
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
      .joinToString("\n") {
        val length = it.key
        val rollbacks = it.value.get()
        val tokensRolled = length * rollbacks
        "$length;$rollbacks;$tokensRolled;" +
        "${if (lexemeCount == 0) "n/a" else tokensRolled * 100 / lexemeCount}%;" +
        "${if (totalRollbacks == 0) "n/a" else rollbacks * 100 / totalRollbacks}%;" +
        "${if (totalRolledback == 0) "n/a" else tokensRolled * 100 / totalRolledback}%"
      }

    val rollbackSources = traces.entries.asSequence()
      .sortedBy { entry -> -entry.value.tokens }
      .joinToString("\n") { entry -> "${entry.key};${entry.value.rollbacks};${entry.value.tokens}" }

    return """Summary:
      
          Passes: $builders
    Tokens count: $lexemeCount
 Total rollbacks: $totalRollbacks
     Rolled back: $totalRolledback (${totalRolledback * 100 / lexemeCount}%) tokens

Passes CSV data:

Characters;Tokens
$passes

Rollbacks CSV data:

Tokens;Rollbacks;Tokens rolled;% of tokens count;% of rollbacks;% of rolled back
$rollbacks
        """.trimIndent() + if (rollbackSources.isEmpty()) ""
    else """

Rollback traces CSV stat:

Invocation point;Rollbacks;Tokens
$rollbackSources
        """
  }

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

  private data class StatEntry(var rollbacks: Int = 0, var tokens: Int = 0) {
    fun registerRollback(tokens: Int) {
      rollbacks++
      this.tokens += tokens
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