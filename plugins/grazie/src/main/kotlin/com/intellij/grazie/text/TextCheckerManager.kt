package com.intellij.grazie.text

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.utils.isSpelling
import com.intellij.openapi.diagnostic.thisLogger

internal class TextCheckerManager {

  companion object {
    @Deprecated("Use doRun(checkers, contexts) instead")
    fun doRun(checkers: List<TextChecker>, context: ProofreadingContext): Collection<TextProblem> = doRun(checkers, listOf(context))

    fun doRun(checkers: List<TextChecker>, contexts: List<ProofreadingContext>): Collection<TextProblem> {
      if (checkers.isSingleSpellingChecker()) {
        return catching { checkers.first().check(contexts) } ?: emptyList()
      }

      return checkers.flatMap { checker ->
        catching { checker.check(contexts) } ?: emptyList()
      }
    }

    private inline fun <T> catching(block: () -> T): T? {
      try {
        return block()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        thisLogger().error(e)
        return null
      }
    }

    private fun List<TextChecker>.isSingleSpellingChecker(): Boolean {
      return size == 1 && first().isSpelling()
    }
  }
}
