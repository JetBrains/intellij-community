// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun <T> fn(<caret>ctx: Ctx, x: T): T = x

fun usage() {
    val ref: (Ctx, Int) -> Int = ::fn
}
