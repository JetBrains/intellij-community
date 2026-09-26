import kotlin.contracts.InvocationKind

inline fun foo(block: () -> Unit) {
    <selection>kotlin.contracts.contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }</selection>

    block()
}
