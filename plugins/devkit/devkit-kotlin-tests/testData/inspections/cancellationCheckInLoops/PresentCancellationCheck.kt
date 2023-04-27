package inspections.cancellationCheckInLoops

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCancelled
import com.intellij.util.concurrency.annotations.RequiresReadLock


@RequiresReadLock
fun main() {
  for (i in 1..10) {
    ProgressManager.checkCanceled()
  }
}

@RequiresReadLock
suspend fun foo() {
  // right cancellaiton check
  for (i in 1..10) {
    checkCancelled()
  }

  // wrong cancellation check
  <warning descr="Cancellation check 'com.intellij.openapi.progress.CoroutinesKt.checkCancelled' should be placed in the first line">for</warning> (i in 1..10) {
    ProgressManager.checkCanceled()
  }
}