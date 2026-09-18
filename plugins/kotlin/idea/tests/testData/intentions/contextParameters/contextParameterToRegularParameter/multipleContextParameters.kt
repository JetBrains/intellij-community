// SHOULD_FAIL_WITH: Callable reference '::fn' will no longer match the expected function type
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class CtxA
class CtxB

context(<caret>a: CtxA, b: CtxB)
fun fn(x: Int): String = x.toString()

context(a: CtxA, b: CtxB)
fun usage() {
    val ref: (Int) -> String = ::fn
}
