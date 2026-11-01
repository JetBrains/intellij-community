import com.intellij.diagnostic.ControlFlowExceptionsKt;
import com.intellij.openapi.progress.ProgressManager;

class InsertRethrowHelperInBroadCatch {
  void m() {
    try {
      ProgressManager.checkCanceled();
    }
    catch (Throwable e) {
        ControlFlowExceptionsKt.rethrowControlFlowException(e);
        handle(e);
    }
  }

  void handle(Throwable e) {}
}
