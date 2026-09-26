import com.example.SubclassOfCancellationException
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException

private val LOG = Logger.getInstance("any")

fun launch(block: suspend () -> Unit) {
    // ...
}

class IncorrectCeHandlingInSuspendingLambdasTests {
  fun testCeSwallowed() {
    launch {
      try {
        // anything
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        // exception swallowed
      }
    }
  }

  fun testCeLogged() {
    launch {
      try {
        // anything
      }
      catch (e: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
        throw e
      }
    }
  }

  fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    launch {
      try {
        // anything
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">info("Error occured", e)</error>
      }
    }
  }

  fun testSwallowedAndLoggedOnInfoLevel() {
    launch {
      try {
        // anything
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">info(e)</error>
      }
    }
  }

  fun testSwallowedAndLoggedOnErrorLevel() {
    launch {
      try {
        // anything
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error(e)</error>
      }
    }
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    launch {
      try {
        // anything
      }
      catch (<error descr="'kotlinx.coroutines.CancellationException' must be rethrown">e</error>: CancellationException) {
        LOG.<error descr="'kotlinx.coroutines.CancellationException' must not be logged">error("Error occurred: " + e.message)</error>
      }
    }
  }

  // should not report subclasses
  fun testCeInheritorSwallowedAndLogger() {
    launch {
      try {
        // anything
      }
      catch (e: SubclassOfCancellationException) {
        LOG.error(e)
      }
    }
  }

}