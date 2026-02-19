import com.example.SubclassOfCancellationException
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException

private val LOG = Logger.getInstance("any")

class IncorrectCeHandlingWhenCeCaughtImplicitlyTests {

  // tests for CancellationException
  @Throws(CancellationException::class)
  fun throwCe() {
    // anything
  }

  fun testCeSwallowed() {
    try {
      throwCe()
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown. It is thrown by 'throwCe()'.">e</error>: Exception) {
      // exception swallowed
    }
  }

  fun testCeLogged() {
    try {
      throwCe()
    }
    catch (e: Exception) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged. It is thrown by 'throwCe()'.">error(e)</error>
      throw e
    }
  }

  fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      throwCe()
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown. It is thrown by 'throwCe()'.">e</error>: Exception) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged. It is thrown by 'throwCe()'.">info("Error occured", e)</error>
    }
  }

  fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      throwCe()
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown. It is thrown by 'throwCe()'.">e</error>: Exception) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged. It is thrown by 'throwCe()'.">info(e)</error>
    }
  }

  fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      throwCe()
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown. It is thrown by 'throwCe()'.">e</error>: Exception) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged. It is thrown by 'throwCe()'.">error(e)</error>
    }
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      throwCe()
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown. It is thrown by 'throwCe()'.">e</error>: Exception) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged. It is thrown by 'throwCe()'.">error("Error occurred: " + e.message)</error>
    }
  }

  fun testCeSwallowedAndMultipleGenericCatchClauses() {
    try {
      throwCe()
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown. It is thrown by 'throwCe()'.">e</error>: RuntimeException) {
      // exception swallowed
    }
    catch (e: Exception) {
      // exception swallowed
    }
    catch (e: Throwable) {
      // exception swallowed
    }
  }

  fun testCeLoggedAndMultipleGenericCatchClauses() {
    try {
      throwCe()
    }
    catch (e: RuntimeException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged. It is thrown by 'throwCe()'.">error(e)</error>
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

  fun testNotHandlingOuterTryIfNestedCatchesCe() {
    try {
      // anything
      try {
        throwCe()
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  // tests for CancellationException inheritor; they should not be reported
  @Throws(SubclassOfCancellationException::class)
  fun throwCeInheritor() {
    // anything
  }

  fun testCeInheritorSwallowed() {
    try {
      throwCeInheritor()
    }
    catch (e: Exception) {
      // exception swallowed
    }
  }

  fun testCeInheritorLogged() {
    try {
      throwCeInheritor()
    }
    catch (e: Exception) {
      LOG.error(e)
      throw e
    }
  }

  fun testCeInheritorSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      throwCeInheritor()
    }
    catch (e: Exception) {
      LOG.info("Error occured", e)
    }
  }

  fun testCeInheritorSwallowedAndLoggedOnInfoLevel() {
    try {
      throwCeInheritor()
    }
    catch (e: Exception) {
      LOG.info(e)
    }
  }

  fun testCeInheritorSwallowedAndLoggedOnErrorLevel() {
    try {
      throwCeInheritor()
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  fun testCeInheritorSwallowedAndOnlyExceptionMessageLogged() {
    try {
      throwCeInheritor()
    }
    catch (e: Exception) {
      LOG.error("Error occurred: " + e.message)
    }
  }

  fun testCeInheritorSwallowedAndMultipleGenericCatchClauses() {
    try {
      throwCeInheritor()
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

  fun testCeInheritorLoggedAndMultipleGenericCatchClauses() {
    try {
      throwCeInheritor()
    }
    catch (e: RuntimeException) {
      LOG.error(e)
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

  fun testNotHandlingOuterTryIfNestedCatchesCeInheritor() {
    try {
      // anything
      try {
        throwCeInheritor()
      }
      catch (e: SubclassOfCancellationException) {
        LOG.error(e)
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

}
