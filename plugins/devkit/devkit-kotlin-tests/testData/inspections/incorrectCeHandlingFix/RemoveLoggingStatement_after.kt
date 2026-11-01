import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException

private val LOG = Logger.getInstance("x")

class RemoveLoggingStatement {
  fun m() {
    try {
      work()
    }
    catch (e: ProcessCanceledException) {
        throw e
    }
  }

  fun work() {}
}
