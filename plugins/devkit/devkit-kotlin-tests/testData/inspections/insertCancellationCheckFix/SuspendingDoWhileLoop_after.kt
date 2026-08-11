package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  var i = 0
  do {
      checkCanceled()
      // comments
      doSomething()
      i++
  } while (i < 10)
}