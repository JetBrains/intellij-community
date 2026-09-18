// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx, vararg xs: Int): String = xs.size.toString()

fun usage() {
    val ref: (Ctx, IntArray) -> String = ::fn
}
