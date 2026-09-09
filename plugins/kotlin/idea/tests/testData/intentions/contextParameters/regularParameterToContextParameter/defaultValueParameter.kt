// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx, x: Int = 0): String = x.toString()

fun usage() {
    val ref: (Ctx, Int) -> String = ::fn
}
