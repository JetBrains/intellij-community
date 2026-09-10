// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(): Int = 0

context(<caret>ctx: Ctx)
fun test() {
    val ref: () -> Int = ::fn
}
