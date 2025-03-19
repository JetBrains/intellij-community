package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething

@Suppress("UNUSED_PARAMETER")
fun withSuspendLambda(l: suspend () -> Any) { }

@RequiresReadLock
@Suppress("UNUSED_ANONYMOUS_PARAMETER")
fun test(map: Map<String, String>) {
  withSuspendLambda {
    map.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">for<caret>Each</warning> { k, v ->
        // comments
        doSomething()
    }
  }
}
