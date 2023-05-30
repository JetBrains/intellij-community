import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("any")

class IncorrectPceHandlingWhenPceCaughtImplicitlyTests {
  @Throws(ProcessCanceledException::class)
  fun throwPce() {
    // anything
  }

  fun test1() {
    try {
      throwPce()
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>: Exception) {
      // exception swallowed
    }
  }

  fun test2() {
    try {
      throwPce()
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>: Exception) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">info("Error occurred", e)</warning>
    }
  }

  fun test3() {
    try {
      throwPce()
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>: Exception) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">info(e)</warning>
    }
  }

  fun test4() {
    try {
      throwPce()
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>: Exception) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">error(e)</warning>
    }
  }

  fun test5() {
    try {
      throwPce()
    }
    catch (<warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>: Exception) {
      LOG.<warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">error("Error occurred: " + e.message)</warning>
    }
  }

}
