import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("x")

class RemoveLoggingStatement {
  fun m() {
    try {
      work()
    }
    catch (e: ProcessCanceledException) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged"><caret>error(e)</error>
      throw e
    }
  }

  fun work() {}
}
