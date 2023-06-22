package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock


@RequiresReadLock
fun main() {
  for (i in 1..10) {
      ProgressManager.checkCanceled()
      // comments
  }
}