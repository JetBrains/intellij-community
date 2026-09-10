// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

class A {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()

    context(c: Ctx)
    fun test() {
        val ref: (Int) -> String = ::fn
    }
}
