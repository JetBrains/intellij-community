import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingWhenMultipleCatchClausesTests {
  fun test1() {
    try {
      // anything
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
      // exception swallowed
    }
    catch (e: Exception) {
      // exception swallowed
    }
  }

  fun test2() {
    try {
      // anything
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged">info("Error occurred", e)</warning>
    }
    catch (e: Exception) {
      LOG.info("Error occurred", e)
    }
  }

  fun test3() {
    try {
      // anything
    }
    catch (e: Exception) {
      LOG.info(e)
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged">info(e)</warning>
    }
  }

  fun test4() {
    try {
      // anything
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged">error(e)</warning>
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  fun test5() {
    try {
      // anything
    }
    catch (e: Exception) {
      LOG.error("Error occurred: " + e.message)
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged">error("Error occurred: " + e.message)</warning>
    }
  }
}
