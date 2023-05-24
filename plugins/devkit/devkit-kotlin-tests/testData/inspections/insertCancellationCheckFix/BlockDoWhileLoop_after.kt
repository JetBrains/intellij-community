package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
fun main() {
  var i = 0
  do {
      ProgressManager.checkCanceled()
      // comments
      doSomething()
      i++
  } while (i < 10)
}