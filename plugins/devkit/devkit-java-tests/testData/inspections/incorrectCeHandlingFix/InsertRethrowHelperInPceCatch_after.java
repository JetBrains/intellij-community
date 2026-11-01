import com.intellij.diagnostic.ControlFlowExceptionsKt;
import com.intellij.openapi.progress.ProcessCanceledException;

class InsertRethrowHelperInPceCatch {
  String m() {
    try {
      return compute();
    }
    catch (ProcessCanceledException e) {
        ControlFlowExceptionsKt.rethrowControlFlowException(e);
        return null;
    }
  }

  String compute() { return ""; }
}
