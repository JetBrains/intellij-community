package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import inspections.cancellationCheckInLoops.Foo.doSomething

@RequiresReadLock
fun main(list: Iterable<String>) {
  list.forEach {
      ProgressManager.checkCanceled()
      doSomething()
  }
}
