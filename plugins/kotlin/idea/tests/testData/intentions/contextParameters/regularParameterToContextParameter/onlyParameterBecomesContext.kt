// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx): Int = 0

fun usage() {
    val ref: (Ctx) -> Int = ::fn
}
