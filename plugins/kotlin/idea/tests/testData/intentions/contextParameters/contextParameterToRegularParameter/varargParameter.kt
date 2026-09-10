// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

context(<caret>ctx: Ctx)
fun fn(vararg xs: Int): String = xs.size.toString()

context(c: Ctx)
fun usage() {
    val ref: (IntArray) -> String = ::fn
}
