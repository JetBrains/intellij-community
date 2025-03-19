package inspections.cancellationCheckInLoops

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
fun main() {
  var i = 0
  while (i < 100) {
    // nested loops with something in between
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 10) {
      doSomething()
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 20) {
        doSomething()
        i++
      }
      i++
    }

    // sibling loop
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 30) {
      doSomething()
      i++
    }

    // nested loops with a block in between
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 40) {
      if (i < 5) {
        // empty loop
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 3) {
        }
      }
    }

    // single-line loop
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 50) doSomething()
    i++
  }
}