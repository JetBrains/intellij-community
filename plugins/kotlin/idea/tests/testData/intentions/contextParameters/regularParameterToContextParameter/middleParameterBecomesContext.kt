// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(a: Int, <caret>ctx: Ctx, b: String): String = "$a$b"

fun usage() {
    val ref: (Int, Ctx, String) -> String = ::fn
}
