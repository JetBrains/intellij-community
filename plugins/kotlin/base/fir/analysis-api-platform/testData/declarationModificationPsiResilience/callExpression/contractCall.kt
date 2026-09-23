import kotlin.contracts.contract
import kotlin.contracts.InvocationKind

inline fun foo(block: () -> Unit) {
    <selection>contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }</selection>

    block()
}
