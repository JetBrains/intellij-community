// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference '::fn' cannot be updated automatically because parameters were converted to/from context parameters

class CtxA
class CtxB

context(a: CtxA)
fun fn(<caret>b: CtxB, x: Int): String = x.toString()

context(a: CtxA)
fun usage() {
    val ref: (CtxB, Int) -> String = ::fn
}
