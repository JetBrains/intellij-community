import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.CancellationException

fun controlFlowGuard(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e is ControlFlowException) throw e</weak_warning>
}

fun processCanceledGuard(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e is ProcessCanceledException) throw e</weak_warning>
}

fun cancellationExceptionGuard(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e is CancellationException) throw e</weak_warning>
}

fun shouldRethrowGuard(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (Logger.shouldRethrow(e)) throw e</weak_warning>
}

fun combinedGuard(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e is CancellationException || e is ControlFlowException) throw e</weak_warning>
}

fun negatedIsNotFlagged(e: Throwable) {
  if (e !is ControlFlowException) throw e
}

fun unrelatedTypeNotFlagged(e: Throwable) {
  if (e is IllegalArgumentException) throw e
}

fun unrelatedConditionNotFlagged(e: Throwable, cond: Boolean) {
  if (cond) throw e
}

fun andCombinedNotFlagged(e: Throwable, cond: Boolean) {
  if (cond && e is ControlFlowException) throw e
}

fun partialOrNotFlagged(e: Throwable, cond: Boolean) {
  if (e is ControlFlowException || cond) throw e
}

fun nonEmptyElseNotFlagged(e: Throwable) {
  if (e is ControlFlowException) throw e else println()
}

fun multiStatementThenNotFlagged(e: Throwable) {
  if (e is ControlFlowException) {
    println()
    throw e
  }
}
