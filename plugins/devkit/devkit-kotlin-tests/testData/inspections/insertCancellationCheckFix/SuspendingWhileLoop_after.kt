package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
suspend fun main() {
  var i = 0
  while (i < 10) {
      checkCanceled()
      // comments
      doSomething()
      i++
  }
}