// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

context(<caret>_: Ctx)
fun fn(x: Int): String = x.toString()

context(c: Ctx)
fun usage() {
    val ref: (Int) -> String = ::fn
}
