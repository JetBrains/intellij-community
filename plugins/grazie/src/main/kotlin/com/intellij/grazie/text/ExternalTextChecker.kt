package com.intellij.grazie.text

import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.runBlockingCancellable
import org.jetbrains.annotations.ApiStatus

/** A checker that might wait for something (e.g. a web request) without consuming CPU */
abstract class ExternalTextChecker : TextChecker() {
  override fun check(extracted: TextContent): Collection<TextProblem> =
    runBlockingCancellable { checkExternally(extracted) }

  override fun check(context: ProofreadingContext): Collection<TextProblem> =
    runBlockingCancellable { checkExternally(context) }

  override fun check(contexts: List<ProofreadingContext>): Collection<TextProblem> =
    runBlockingCancellable { checkExternally(contexts) }

  @Deprecated("Use checkExternally(ProofreadingContext) instead", ReplaceWith("checkExternally(ProofreadingContext)"))
  @ApiStatus.ScheduledForRemoval
  open suspend fun checkExternally(content: TextContent): Collection<TextProblem> =
    throw UnsupportedOperationException("Use checkExternally(context: ProofreadingContext) instead")

  /**
   * Check a text for problems in the same way as [TextChecker.check], possibly suspending in the middle to perform a web request
   * (e.g., using `withContext (Dispatchers.IO)`
   * and possibly [com.intellij.openapi.application.ex.ApplicationUtil.runWithCheckCanceled]).
   * In that case, another [TextChecker] can be invoked to avoid wasting the CPU time.
   * This function is invoked inside a read action.
   */
  open suspend fun checkExternally(context: ProofreadingContext): Collection<TextProblem> = checkExternally(context.text)

  /**
   * Perform the checks on the given contexts.
   * The implementations should check [com.intellij.grazie.GrazieConfig.State] for enabled/disabled rules.
   *
   * The difference between this method and [checkExternally] is that this method
   * is capable of finding additional problems that are not present in the single context check.
   *
   * The default implementation is provided to preserve backward compatibility.
   */
  open suspend fun checkExternally(contexts: List<ProofreadingContext>): Collection<TextProblem> {
    val problems = mutableListOf<TextProblem>()
    for (context in contexts) {
      problems.addAll(checkExternally(context))
      checkCanceled()
    }
    return problems
  }
}