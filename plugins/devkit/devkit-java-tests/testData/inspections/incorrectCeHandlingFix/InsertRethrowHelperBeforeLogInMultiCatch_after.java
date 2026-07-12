import com.intellij.diagnostic.ControlFlowExceptionsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class InsertRethrowHelperBeforeLogInMultiCatch {
  private static final Logger LOG = Logger.getInstance(InsertRethrowHelperBeforeLogInMultiCatch.class);

  void m() {
    try {
      // anything
    }
    catch (IllegalArgumentException | ProcessCanceledException e) {
        ControlFlowExceptionsKt.rethrowControlFlowException(e);
        LOG.error(e);
    }
  }
}
