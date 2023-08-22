package inspections.cancellationCheckInLoops

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
fun main() {
  var j = 0
  // nested loops of different kinds
  for (i in 1..10) {
    while (j < 5) {
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
        doSomething()
        j++
      } while (j < 3)
      j++
    }
  }

  // single line nested loops
  for (i in 1..10) <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (j < 5) {
    doSomething()
    j++
  }

}