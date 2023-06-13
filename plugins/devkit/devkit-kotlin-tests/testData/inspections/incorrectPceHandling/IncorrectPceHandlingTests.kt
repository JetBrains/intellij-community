import com.example.SubclassOfProcessCanceledException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingTests {
  fun testPceSwallowed() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      // exception swallowed
    }
  }

  fun testPceLogged() {
    try {
      // anything
    }
    catch (e: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">error(e)</error>
      throw e
    }
  }

  fun testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">info("Error occured", e)</error>
    }
  }

  fun testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">info(e)</error>
    }
  }

  fun testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">error(e)</error>
    }
  }

  fun testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">error("Error occurred: " + e.message)</error>
    }
  }

  fun testPceInheritorSwallowedAndLogger() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' inheritor must be rethrown">e</error>: SubclassOfProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' inheritor must not be logged">error(e)</error>
    }
  }

}
