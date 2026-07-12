import com.intellij.openapi.diagnostic.Logger;

class ReplaceShouldRethrowGuard {
  void m(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'"><caret>if (Logger.shouldRethrow(e)) throw e;</weak_warning>
  }
}
