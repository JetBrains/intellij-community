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
    catch (<error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>: Exception) {
      // exception swallowed
    }
  }

  fun test2() {
    try {
      throwPce()
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">info("Error occurred", e)</error>
    }
  }

  fun test3() {
    try {
      throwPce()
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">info(e)</error>
    }
  }

  fun test4() {
    try {
      throwPce()
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">error(e)</error>
    }
  }

  fun test5() {
    try {
      throwPce()
    }
    catch (<error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>: Exception) {
      LOG.<error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">error("Error occurred: " + e.message)</error>
    }
  }

}
