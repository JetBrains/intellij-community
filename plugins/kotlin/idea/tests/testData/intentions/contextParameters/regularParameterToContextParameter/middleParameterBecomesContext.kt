// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(a: Int, <caret>ctx: Ctx, b: String): String = "$a$b"

fun usage() {
    val ref: (Int, Ctx, String) -> String = ::fn
}
