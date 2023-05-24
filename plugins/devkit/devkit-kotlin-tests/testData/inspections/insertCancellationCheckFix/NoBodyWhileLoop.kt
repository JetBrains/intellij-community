package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock


@RequiresReadLock
fun main() {
  var i = 0
  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">while<caret></warning> (i < 10);
}