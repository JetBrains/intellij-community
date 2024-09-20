import com.example.SubclassOfProcessCanceledException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingWhenPceCaughtImplicitlyTests {

  // tests for ProcessCanceledException
  @Throws(ProcessCanceledException::class)
  fun throwPce() {
    // anything
  }

  fun testPceSwallowed() {
    try {
      throwPce()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>: Exception) {
      // exception swallowed
    }
  }

  fun testPceLogged() {
    try {
      throwPce()
    }
    catch (e: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">error(e)</error>
      throw e
    }
  }

  fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      throwPce()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">info("Error occured", e)</error>
    }
  }

  fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      throwPce()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">info(e)</error>
    }
  }

  fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      throwPce()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">error(e)</error>
    }
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      throwPce()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">error("Error occurred: " + e.message)</error>
    }
  }

  fun testPceSwallowedAndMultipleGenericCatchClauses() {
    try {
      throwPce()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>: RuntimeException) {
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
      throwPce()
    }
    catch (e: RuntimeException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">error(e)</error>
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      throw e
    }
  }

  fun testNotHandlingOuterTryIfNestedCatchesPce() {
    try {
      // anything
      try {
        throwPce()
      }
      catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
        LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">error(e)</error>
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  // tests for ProcessCanceledException inheritor
  @Throws(SubclassOfProcessCanceledException::class)
  fun throwPceInheritor() {
    // anything
  }

  fun testPceInheritorSwallowed() {
    try {
      throwPceInheritor()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>: Exception) {
      // exception swallowed
    }
  }

  fun testPceInheritorLogged() {
    try {
      throwPceInheritor()
    }
    catch (e: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">error(e)</error>
      throw e
    }
  }

  fun testPceInheritorSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      throwPceInheritor()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">info("Error occured", e)</error>
    }
  }

  fun testPceInheritorSwallowedAndLoggedOnInfoLevel() {
    try {
      throwPceInheritor()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">info(e)</error>
    }
  }

  fun testPceInheritorSwallowedAndLoggedOnErrorLevel() {
    try {
      throwPceInheritor()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">error(e)</error>
    }
  }

  fun testPceInheritorSwallowedAndOnlyExceptionMessageLogged() {
    try {
      throwPceInheritor()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">error("Error occurred: " + e.message)</error>
    }
  }

  fun testPceInheritorSwallowedAndMultipleGenericCatchClauses() {
    try {
      throwPceInheritor()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>: RuntimeException) {
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
      throwPceInheritor()
    }
    catch (e: RuntimeException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">error(e)</error>
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      throw e
    }
  }

  fun testNotHandlingOuterTryIfNestedCatchesPceInheritor() {
    try {
      // anything
      try {
        throwPceInheritor()
      }
      catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>: SubclassOfProcessCanceledException) {
        LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">error(e)</error>
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

}
