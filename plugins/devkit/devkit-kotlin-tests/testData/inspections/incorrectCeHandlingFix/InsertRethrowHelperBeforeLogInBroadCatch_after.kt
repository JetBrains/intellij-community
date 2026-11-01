import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager

private val LOG = Logger.getInstance("x")

class InsertRethrowHelperBeforeLogInBroadCatch {
  fun m() {
    try {
      ProgressManager.checkCanceled()
    }
    catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.error(e)
    }
  }
}
