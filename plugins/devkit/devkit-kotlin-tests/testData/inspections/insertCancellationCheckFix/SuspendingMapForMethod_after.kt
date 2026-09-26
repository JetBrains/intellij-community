package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething

@Suppress("UNUSED_PARAMETER")
fun withSuspendLambda(l: suspend () -> Any) { }

@RequiresReadLock
@Suppress("UNUSED_ANONYMOUS_PARAMETER")
fun test(map: Map<String, String>) {
  withSuspendLambda {
    map.forEach { k, v ->
        checkCanceled()
        // comments
        doSomething()
    }
  }
}
