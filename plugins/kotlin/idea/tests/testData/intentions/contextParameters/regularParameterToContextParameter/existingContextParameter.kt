// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'b'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

class CtxA
class CtxB

context(a: CtxA)
fun fn(<caret>b: CtxB, x: Int): String = x.toString()

context(a: CtxA)
fun usage() {
    val ref: (CtxB, Int) -> String = ::fn
}
