import kotlin.contracts.contract
import kotlin.contracts.InvocationKind

inline fun foo(block: () -> Unit) {
    <caret>contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    block()
}

// TYPE: if (1 == 2)
// OUT_OF_BLOCK: true
