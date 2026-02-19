import com.example.SubclassOfCancellationException
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException

private val LOG = Logger.getInstance("any")

class IncorrectCeHandlingWhenMultipleCatchClausesTests {
  suspend fun testCeSwallowed() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      // exception swallowed
    }
    catch (e: Exception) {
      // exception swallowed
    }
  }

  suspend fun testCeLogged() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
      throw e
    }
    catch (e: Exception) {
      // exception swallowed
    }
  }

  suspend fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">info("Error occured", e)</error>
    }
    catch (e: Exception) {
      LOG.info("Error occured", e)
    }
  }

  suspend fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">info(e)</error>
    }
    catch (e: Exception) {
      LOG.info(e)
    }
  }

  suspend fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  suspend fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error("Error occurred: " + e.message)</error>
    }
    catch (e: Exception) {
      LOG.error("Error occurred: " + e.message)
    }
  }

  suspend fun testCeSwallowedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
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

  suspend fun testCeLoggedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    catch (e: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
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

  suspend fun testCeInheritorSwallowedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    // should not report subclasses of CancellationException:
    catch (e: SubclassOfCancellationException) {
      // exception swallowed
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
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

  suspend fun testCeInheritorLoggedAndMultipleGenericCatchClauses() {
    try {
      // anything
    }
    // subclasses should not be reported:
    catch (e: SubclassOfCancellationException) {
      LOG.error(e)
      throw e
    }
    catch (e: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
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

  suspend fun testNotHandlingOuterTryIfNestedCatchesCe() {
    try {
      // anything
      try {
        // anything
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

}