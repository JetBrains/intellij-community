import kotlin.contracts.contract
import kotlin.contracts.InvocationKind

inline fun foo(block: () -> Unit) {
    <caret>contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    block()
}

// DELETE_LINE
// OUT_OF_BLOCK: true
