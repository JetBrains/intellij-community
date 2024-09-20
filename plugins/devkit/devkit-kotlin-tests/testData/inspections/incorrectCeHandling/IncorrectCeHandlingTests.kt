import com.example.SubclassOfCancellationException
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException

private val LOG = Logger.getInstance("any")

class IncorrectCeHandlingTests {
  suspend fun testCeSwallowed() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
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
  }

  suspend fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">info("Error occured", e)</error>
    }
  }

  suspend fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">info(e)</error>
    }
  }

  suspend fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
    }
  }

  suspend fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    }
    catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
      LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error("Error occurred: " + e.message)</error>
    }
  }

  // should not report subclasses
  suspend fun testCeInheritorSwallowedAndLogger() {
    try {
      // anything
    }
    catch (e: SubclassOfCancellationException) {
      LOG.error(e)
    }
  }

}