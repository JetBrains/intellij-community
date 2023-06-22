package inspections.cancellationCheckInLoops

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething

@Suppress("UNUSED_PARAMETER")
fun withSuspendLambda(l: suspend () -> Any) { }

@RequiresReadLock
suspend fun mySuspendFun() {
  <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">for</warning> (i in 1..10) {
    doSomething()
  }
}

@RequiresReadLock
fun main() {
  withSuspendLambda {
    <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">for</warning> (i in 1..10) {
      doSomething()
    }
  }
}

