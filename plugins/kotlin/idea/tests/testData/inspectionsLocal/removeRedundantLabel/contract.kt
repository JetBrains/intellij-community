// WITH_STDLIB
// K2_ERROR: CONTRACT_NOT_ALLOWED

import kotlin.contracts.*

@OptIn(ExperimentalContracts::class)
inline fun case_5(block: () -> Unit) {
    <caret>test@ contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block()
}