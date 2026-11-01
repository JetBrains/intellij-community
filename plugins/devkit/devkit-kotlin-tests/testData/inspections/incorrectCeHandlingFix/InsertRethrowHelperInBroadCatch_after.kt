import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.progress.ProgressManager

class InsertRethrowHelperInBroadCatch {
  fun m() {
    try {
      ProgressManager.checkCanceled()
    }
    catch (e: Throwable) {
        rethrowControlFlowException(e)
        handle(e)
    }
  }

  fun handle(e: Throwable) {}
}
