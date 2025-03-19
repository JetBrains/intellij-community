import kotlin.contracts.contract
import kotlin.contracts.InvocationKind

inline fun foo(block: () -> Unit) {
    <caret>
    block()
}

// TYPE: contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
// OUT_OF_BLOCK: true
