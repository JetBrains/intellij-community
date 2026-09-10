// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

context(<caret>ctx: Ctx)
fun fn(x: Int): String = x.toString()

fun accept(f: (Int) -> String) {}

context(c: Ctx)
fun usage1() {
    val ref: (Int) -> String = ::fn
}

context(c: Ctx)
fun usage2() {
    accept(::fn)
}
