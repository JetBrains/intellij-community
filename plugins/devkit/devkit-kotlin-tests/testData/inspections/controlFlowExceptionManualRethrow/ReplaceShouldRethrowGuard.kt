import com.intellij.openapi.diagnostic.Logger

fun m(e: Throwable) {
  <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'"><caret>if (Logger.shouldRethrow(e)) throw e</weak_warning>
}
