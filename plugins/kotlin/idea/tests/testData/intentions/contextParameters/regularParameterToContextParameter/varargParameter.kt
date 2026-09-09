// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx, vararg xs: Int): String = xs.size.toString()

fun usage() {
    val ref: (Ctx, IntArray) -> String = ::fn
}
