package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


@RequiresReadLock
fun main() {
  var i = 0
  while (i < 10) {
      ProgressManager.checkCanceled()
      // comments
      doSomething()
      i++
  }
}