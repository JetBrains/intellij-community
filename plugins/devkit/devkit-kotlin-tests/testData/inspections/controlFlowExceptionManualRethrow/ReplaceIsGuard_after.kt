import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.ControlFlowException

fun m(e: Throwable) {
    rethrowControlFlowException(e)
}
