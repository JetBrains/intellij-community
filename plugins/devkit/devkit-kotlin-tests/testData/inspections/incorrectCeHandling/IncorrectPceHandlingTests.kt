import com.example.SubclassOfProcessCanceledException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingTests {
  fun testPceSwallowed() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      // exception swallowed
    }
  }

  fun testPceLogged() {
    try {
      // anything
    }
    catch (e: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
      throw e
    }
  }

  fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">info("Error occured", e)</error>
    }
  }

  fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">info(e)</error>
    }
  }

  fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
    }
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.error("Error occurred: " + e.message)
    }
  }

  fun testPceInheritorSwallowedAndLogger() {
    try {
      // anything
    }
    catch (<error descr="'com.example.SubclassOfProcessCanceledException' must be rethrown">e</error>: SubclassOfProcessCanceledException) {
      LOG.<error descr="'com.example.SubclassOfProcessCanceledException' must not be logged">error(e)</error>
    }
  }

  fun testSwallowedAndLoggedOnWarnLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">warn(e)</error>
    }
  }

  fun testSwallowedAndLoggedOnDebugLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">debug(e)</error>
    }
  }

  fun testSwallowedAndLoggedWithDebug() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">warnWithDebug(e)</error>
    }
  }

  fun testRegularExceptionNotFlagged() {
    try {
    }
    catch (e: RuntimeException) {
      LOG.error(e)
    }
  }


  // IJPL-248976
  fun testExceptionRethrownInParensNotFlagged() {
    try {
      // anything
    } catch (e: ProcessCanceledException) {
      throw (e)
    }
  }

  private fun rethrowControlFlowException(t: Throwable?) { /* API stub */ }

  // No call in the 'try' is proven to throw the current cancellation, so the 'CancellationException' may belong to an
  // awaited task.
  fun testCancellationExceptionWithoutProvenSourceNotFlagged() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      // exception swallowed
    }
  }

  // A 'checkCanceled()' call proves the caught 'CancellationException' can be the current computation's cancellation.
  fun testCancellationExceptionWithProvenCurrentSourceSwallowed() {
    try {
      ProgressManager.checkCanceled()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'.">e</error>: CancellationException) {
      // exception swallowed
    }
  }

  fun testCancellationExceptionWrappedAndRethrownNotFlagged() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      throw CeProcessCanceledException(e)
    }
  }

  // 'rethrowControlFlowException(e.cause)' rethrows only the cause, not 'e', so it does not rethrow the caught exception.
  fun testCancellationExceptionCauseRethrowStillFlagged() {
    try {
      ProgressManager.checkCanceled()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'.">e</error>: CancellationException) {
      rethrowControlFlowException(e.cause)
    }
  }

  // 'ensureActive()' of 'kotlinx.coroutines' proves it too.
  fun testCancellationExceptionWithEnsureActiveSwallowed(job: Job) {
    try {
      job.ensureActive()
    }
    catch (<error descr="'java.util.concurrent.CancellationException' must be rethrown. It is thrown by 'ensureActive()'.">e</error>: CancellationException) {
      // exception swallowed
    }
  }

  // An 'ensureActive()' in the catch body rethrows the current cancellation.
  fun testPceRethrownByEnsureActiveNotFlagged(job: Job) {
    try {
      // anything
    }
    catch (e: ProcessCanceledException) {
      job.ensureActive()
    }
  }

  // Methods that are only named like cancellation checks prove nothing.
  fun testUnrelatedCancellationChecksNotFlagged() {
    try {
      checkCanceled()
      ensureActive()
    }
    catch (e: CancellationException) {
      // exception swallowed
    }
  }

  private fun checkCanceled() {}

  private fun ensureActive() {}

}
