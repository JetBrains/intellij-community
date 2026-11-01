import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager

private val LOG = Logger.getInstance("x")

class InsertRethrowHelperBeforeLogInBroadCatch {
  fun m() {
    try {
      ProgressManager.checkCanceled()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'.">e</error>: Exception) {
      LOG.<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'checkCanceled()'."><caret>error(e)</error>
    }
  }
}
