// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'foo::fn' cannot reference a member extension function and will not be updated

class Ctx

class Foo {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()
}

context(c: Ctx)
fun usage(foo: Foo) {
    val ref: (Int) -> String = foo::fn
}
