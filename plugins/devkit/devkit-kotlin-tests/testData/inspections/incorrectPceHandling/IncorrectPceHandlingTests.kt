import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingTests {
  fun test1() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
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
  }

  fun test3() {
    try {
      // anything
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
  }

  fun test5() {
    try {
      // anything
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown">e</error>: ProcessCanceledException) {
      LOG.<error descr="'ProcessCanceledException' must not be logged">error("Error occurred: " + e.message)</error>
    }
  }
}
