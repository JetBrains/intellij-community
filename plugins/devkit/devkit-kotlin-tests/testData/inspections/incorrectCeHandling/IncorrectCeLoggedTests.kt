import com.example.SubclassOfProcessCanceledException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException

private val LOG = Logger.getInstance("any")

class IncorrectCeLoggedTests {
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

  fun testPceInheritorSwallowedAndLogger() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>: SubclassOfProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">error(e)</error>
    }
  }

  fun testPceLoggedOutsideCatchBlock(e: ProcessCanceledException) {
    LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
  }

  fun testPceInheritorLoggedOutsideCatchBlock(e: SubclassOfProcessCanceledException) {
    LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">error(e)</error>
  }

  fun testPceLoggedAsSecondArgOutsideCatchBlock(e: ProcessCanceledException) {
    LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error("something went wrong", e)</error>
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.error("Error occurred: " + e.message)
    }
  }

  fun testRegularExceptionNotFlagged() {
    try {
    }
    catch (e: RuntimeException) {
      LOG.error(e)
    }
  }

  fun testCeAssignedToThrowableNotFlagged() {
    val t: Throwable = ProcessCanceledException()
    LOG.error(t)
  }

  // kotlinx.coroutines.CancellationException cases:
  // Note: catching CancellationException without rethrowing is not flagged in non-suspend context,
  // but logging it is always forbidden.

  fun testCeLogged() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      LOG.<error descr="'java.util.concurrent.CancellationException' must not be logged">error(e)</error>
      throw e
    }
  }

  fun testCeSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      LOG.<error descr="'java.util.concurrent.CancellationException' must not be logged">info("Error occurred", e)</error>
    }
  }

  fun testCeSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      LOG.<error descr="'java.util.concurrent.CancellationException' must not be logged">error(e)</error>
    }
  }

  fun testCeLoggedOutsideCatchBlock(e: CancellationException) {
    LOG.<error descr="'java.util.concurrent.CancellationException' must not be logged">error(e)</error>
  }

  fun testCeLoggedAsSecondArgOutsideCatchBlock(e: CancellationException) {
    LOG.<error descr="'java.util.concurrent.CancellationException' must not be logged">error("something went wrong", e)</error>
  }

}
