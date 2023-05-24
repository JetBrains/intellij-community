package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  var i = 0
  <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be placed in the first line">do<caret></warning> {
    // comments
    doSomething()
    i++
  } while (i < 10)
}