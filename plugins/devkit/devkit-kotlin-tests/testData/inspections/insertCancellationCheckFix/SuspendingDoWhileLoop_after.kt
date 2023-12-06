package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.checkCancelled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  var i = 0
  do {
      checkCancelled()
      // comments
      doSomething()
      i++
  } while (i < 10)
}