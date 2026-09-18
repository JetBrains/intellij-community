// SHOULD_FAIL_WITH: Callable reference 'this::fn' will no longer match the expected function type
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

class Ctx

class Foo {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()

    context(c: Ctx)
    fun test() {
        val ref: (Int) -> String = this::fn
    }
}
