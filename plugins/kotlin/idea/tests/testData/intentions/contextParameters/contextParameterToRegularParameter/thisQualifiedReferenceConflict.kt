// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'this::fn' cannot be updated automatically because parameters were converted to/from context parameters

class Ctx

class Foo {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()

    context(c: Ctx)
    fun test() {
        val ref: (Int) -> String = this::fn
    }
}
