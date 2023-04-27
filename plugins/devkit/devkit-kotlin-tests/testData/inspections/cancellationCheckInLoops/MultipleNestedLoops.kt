package inspections.cancellationCheckInLoops

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
fun main() {
  for (i in 1..10) {
    // nested loops with something in between
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for</warning> (j in 1..10) {
      doSomething()
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for</warning> (k in 1..10) {
      doSomething()
    }
    }

    // sibling loop
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for</warning> (j in 1..10) {
      doSomething()
    }

    // nested loops with a block in between
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for</warning> (j in 1..10) {
      if (i < 5) {
        // empty loop
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for</warning> (<warning descr="[NAME_SHADOWING] Name shadowed: j">j</warning> in 1..10) {
        }
      }
    }

    // single-line loop
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for</warning> (j in 1..10) doSomething()
  }
}