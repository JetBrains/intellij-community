// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(): Int = 0

fun test(<caret>ctx: Ctx) {
    val ref: () -> Int = ::fn
}
