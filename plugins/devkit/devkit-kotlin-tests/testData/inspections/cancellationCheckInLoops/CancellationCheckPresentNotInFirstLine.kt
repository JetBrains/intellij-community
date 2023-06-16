package inspections.cancellationCheckInLoops

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCancelled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething



@RequiresReadLock
fun main() {
  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (i in 1..10) {
    doSomething()
    ProgressManager.checkCanceled()
  }

  var i = 0;
  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 5) {
    doSomething()
    i++
    ProgressManager.checkCanceled()
  }

  <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
    doSomething()
    ProgressManager.checkCanceled()
    i++
  } while (i < 10)
}

@RequiresReadLock
suspend fun foo() {
  <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">for</warning> (i in 1..10) {
    doSomething()
    checkCancelled()
  }
}