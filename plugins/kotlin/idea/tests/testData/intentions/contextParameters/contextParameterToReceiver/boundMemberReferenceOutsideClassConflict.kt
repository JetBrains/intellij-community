// SHOULD_FAIL_WITH: Callable reference 'foo::fn' cannot be updated to a member with an extension receiver
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

class Ctx

class Foo {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()
}

context(c: Ctx)
fun usage(foo: Foo) {
    val ref: (Int) -> String = foo::fn
}
