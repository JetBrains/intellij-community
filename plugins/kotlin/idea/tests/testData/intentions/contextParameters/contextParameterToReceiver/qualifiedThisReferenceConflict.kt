// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'this::fn' cannot reference a member extension function and will not be updated

class Ctx

class A {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()

    context(c: Ctx)
    fun test() {
        val ref: (Int) -> String = this::fn
    }
}
