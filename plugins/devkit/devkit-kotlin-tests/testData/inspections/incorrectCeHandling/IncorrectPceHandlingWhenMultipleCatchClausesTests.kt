import com.example.SubclassOfProcessCanceledException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingWhenMultipleCatchClausesTests {
  fun testPceSwallowed() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      // exception swallowed
    }
    catch (e: Exception) {
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
    catch (e: Exception) {
      // exception swallowed
    }
  }

  fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">info("Error occured", e)</error>
    }
    catch (e: Exception) {
      LOG.info("Error occured", e)
    }
  }

  fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">info(e)</error>
    }
    catch (e: Exception) {
      LOG.info(e)
    }
  }

  fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error("Error occurred: " + e.message)</error>
    }
    catch (e: Exception) {
      LOG.error("Error occurred: " + e.message)
    }
  }

  fun testPceSwallowedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      // exception swallowed
    }
    catch (e: RuntimeException) {
      // exception swallowed
    }
    catch (e: Exception) {
      // exception swallowed
    }
    catch (e: Throwable) {
      // exception swallowed
    }
  }

  fun testPceLoggedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    catch (e: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
      throw e
    }
    catch (e: RuntimeException) {
      LOG.error(e)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  fun testPceInheritorSwallowedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>: SubclassOfProcessCanceledException) {
      // exception swallowed
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      // exception swallowed
    }
    catch (e: RuntimeException) {
      // exception swallowed
    }
    catch (e: Exception) {
      // exception swallowed
    }
    catch (e: Throwable) {
      // exception swallowed
    }
  }

  fun testPceInheritorLoggedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    catch (e: SubclassOfProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">error(e)</error>
      throw e
    }
    catch (e: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
      throw e
    }
    catch (e: RuntimeException) {
      LOG.error(e)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  fun testNotHandlingOuterTryIfNestedCatchesPce() {
    try {
      // anything
      try {
        // anything
      }
      catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
        LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

}
