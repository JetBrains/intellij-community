import com.intellij.diagnostic.ControlFlowExceptionsKt;
import com.intellij.openapi.diagnostic.Logger;

class ReplaceShouldRethrowGuard {
  void m(Throwable e) throws Throwable {
      ControlFlowExceptionsKt.rethrowControlFlowException(e);
  }
}
