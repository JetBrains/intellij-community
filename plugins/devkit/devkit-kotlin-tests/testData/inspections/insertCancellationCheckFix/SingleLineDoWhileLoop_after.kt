package inspections.insertCancellationCheckFix

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock


@RequiresReadLock
fun main() {
  var i = 0
  do {
      ProgressManager.checkCanceled()
      i++
  } while (i < 10) // comments
}