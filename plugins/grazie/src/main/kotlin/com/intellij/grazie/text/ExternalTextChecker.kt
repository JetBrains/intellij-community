package com.intellij.grazie.text

import com.intellij.openapi.progress.runBlockingCancellable

/** A checker that might wait for something (e.g. a web request) without consuming CPU */
abstract class ExternalTextChecker : TextChecker() {
  override fun check(extracted: TextContent): Collection<TextProblem> =
    runBlockingCancellable { checkExternally(extracted) }

  /**
   * Check a text for problems in the same way as [TextChecker.check], possibly suspending in the middle to perform a web request
   * (e.g. using `withContext (Dispatchers.IO)`
   * and possibly [com.intellij.openapi.application.ex.ApplicationUtil.runWithCheckCanceled]).
   * In that case, another [TextChecker] can be invoked to avoid wasting the CPU time.
   * This function is invoked inside a read action.
   */
  abstract suspend fun checkExternally(content: TextContent): Collection<TextProblem>
}