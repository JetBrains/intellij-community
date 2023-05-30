import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingTests {
  fun test1() {
    try {
      // anything
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
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
  }

  fun test3() {
    try {
      // anything
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
  }

  fun test5() {
    try {
      // anything
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown">e</warning>: ProcessCanceledException) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged">error("Error occurred: " + e.message)</warning>
    }
  }
}
