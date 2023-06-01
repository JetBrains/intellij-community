import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingWhenMultipleCatchClausesTests {
  fun test1() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
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
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">info("Error occurred", e)</error>
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
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">info(e)</error>
    }
  }

  fun test4() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">error(e)</error>
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
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">error("Error occurred: " + e.message)</error>
    }
  }
}
