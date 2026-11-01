import com.intellij.diagnostic.ControlFlowExceptionsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;

class InsertRethrowHelperBeforeLogInBroadCatch {
  private static final Logger LOG = Logger.getInstance(InsertRethrowHelperBeforeLogInBroadCatch.class);

  void m() {
    try {
      ProgressManager.checkCanceled();
    }
    catch (Exception e) {
        ControlFlowExceptionsKt.rethrowControlFlowException(e);
        LOG.error(e);
    }
  }
}
