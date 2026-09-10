// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

context(<caret>ctx: Ctx)
fun fn(): Int = 0

context(c: Ctx)
fun usage() {
    val ref: () -> Int = ::fn
}
