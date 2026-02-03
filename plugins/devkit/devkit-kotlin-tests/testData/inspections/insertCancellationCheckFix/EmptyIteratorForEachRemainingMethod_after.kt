package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import inspections.cancellationCheckInLoops.Foo.doSomething

@RequiresReadLock
fun main(iterator: Iterator<String>) {
  iterator.forEachRemaining {
      ProgressManager.checkCanceled()
  }
}
