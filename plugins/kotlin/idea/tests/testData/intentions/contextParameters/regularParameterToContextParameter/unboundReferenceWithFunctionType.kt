// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun usage() {
    val ref: (Ctx, Int) -> String = ::fn
    ref(Ctx(), 1)
}
