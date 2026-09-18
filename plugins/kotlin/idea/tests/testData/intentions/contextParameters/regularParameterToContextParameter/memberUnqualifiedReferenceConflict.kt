// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

class Ctx

class Foo {
    fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

    fun test() {
        val ref: (Ctx, Int) -> String = ::fn
    }
}
