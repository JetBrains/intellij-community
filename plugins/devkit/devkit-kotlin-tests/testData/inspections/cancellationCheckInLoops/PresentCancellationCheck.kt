package inspections.cancellationCheckInLoops

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCancelled
import com.intellij.util.concurrency.annotations.RequiresReadLock


@RequiresReadLock
fun main() {
  for (i in 1..10) {
    ProgressManager.checkCanceled()
  }

  var i = 0;
  while (i < 5) {
    ProgressManager.checkCanceled()
    i++
  }

  do {
    ProgressManager.checkCanceled()
    i++
  } while (i < 10)
}

@RequiresReadLock
suspend fun foo() {
  // right cancellaiton check
  for (i in 1..10) {
    checkCancelled()
  }

  // wrong cancellation check
  <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be placed in the first line">for</warning> (i in 1..10) {
    ProgressManager.checkCanceled()
  }
}