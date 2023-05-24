package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  var i = 0
  <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be placed in the first line">while<caret></warning> (i < 10) {
    // comments
    doSomething()
    i++
  }
}