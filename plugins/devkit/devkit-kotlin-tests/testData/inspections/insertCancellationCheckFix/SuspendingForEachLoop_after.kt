package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  for (i in 1..10) {
      checkCanceled()
      // comments
      doSomething()
  }
}