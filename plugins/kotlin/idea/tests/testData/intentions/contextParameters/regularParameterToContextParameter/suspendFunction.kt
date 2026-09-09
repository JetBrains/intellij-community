// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

suspend fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun usage() {
    val ref: suspend (Ctx, Int) -> String = ::fn
}
