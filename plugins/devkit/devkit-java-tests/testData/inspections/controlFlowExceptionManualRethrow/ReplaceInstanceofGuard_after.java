import com.intellij.diagnostic.ControlFlowExceptionsKt;
import com.intellij.openapi.diagnostic.ControlFlowException;

class ReplaceInstanceofGuard {
  void m(Throwable e) throws Throwable {
      ControlFlowExceptionsKt.rethrowControlFlowException(e);
  }
}
