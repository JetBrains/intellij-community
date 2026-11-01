import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.progress.ProcessCanceledException

class InsertRethrowHelperInPceCatch {
  fun m(): String? {
    try {
      return compute()
    }
    catch (e: ProcessCanceledException) {
        rethrowControlFlowException(e)
        return null
    }
  }

  fun compute(): String = ""
}
