// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun accept(f: (Ctx, Int) -> String) {}

fun usage1() {
    val ref: (Ctx, Int) -> String = ::fn
}

fun usage2() {
    accept(::fn)
}
