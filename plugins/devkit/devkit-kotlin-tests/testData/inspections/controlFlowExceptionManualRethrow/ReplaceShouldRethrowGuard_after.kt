import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger

fun m(e: Throwable) {
    rethrowControlFlowException(e)
}
