package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething

@Suppress("UNUSED_PARAMETER")
fun withSuspendLambda(l: suspend () -> Any) { }

@RequiresReadLock
fun main() {
  withSuspendLambda {
    for (i in 1..10) {
        checkCanceled()
        // comments
        doSomething()
    }
  }
}