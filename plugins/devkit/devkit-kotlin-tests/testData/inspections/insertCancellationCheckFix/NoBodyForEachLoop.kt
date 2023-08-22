package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock


@RequiresReadLock
fun main() {
  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for<caret></warning> (i in 1..10); // comments
}