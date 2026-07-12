import com.intellij.openapi.diagnostic.ControlFlowException

fun m(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'"><caret>if (e is ControlFlowException) throw e</weak_warning>
}
