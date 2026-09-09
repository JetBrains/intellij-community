// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun <T> fn(<caret>ctx: Ctx, x: T): T = x

fun usage() {
    val ref: (Ctx, Int) -> Int = ::fn
}
