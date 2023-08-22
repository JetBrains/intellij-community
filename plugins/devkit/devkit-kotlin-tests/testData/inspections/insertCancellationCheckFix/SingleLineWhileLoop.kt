package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock

@RequiresReadLock
fun main() {
  var i = 0
  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while<caret></warning> (i < 10) i++ // comments
}