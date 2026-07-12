import com.intellij.openapi.diagnostic.ControlFlowException;

class ReplaceInstanceofGuard {
  void m(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'"><caret>if (e instanceof ControlFlowException) throw e;</weak_warning>
  }
}
