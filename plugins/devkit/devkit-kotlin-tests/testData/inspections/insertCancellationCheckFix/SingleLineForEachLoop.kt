package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
fun main() {
  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for<caret></warning> (i in 1..10) doSomething() // comments
}