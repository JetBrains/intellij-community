package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  var i = 0
  <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">do<caret></warning> {
    // comments
    doSomething()
    i++
  } while (i < 10)
}