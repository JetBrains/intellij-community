// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'foo::fn' cannot be updated automatically because parameters were converted to/from context parameters

class Ctx

class Foo {
    fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()
}

fun usage(foo: Foo) {
    val ref: (Ctx, Int) -> String = foo::fn
}
