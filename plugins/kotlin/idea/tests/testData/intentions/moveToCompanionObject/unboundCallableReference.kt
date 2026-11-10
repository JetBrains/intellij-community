// SHOULD_FAIL_WITH: Callable reference won't be processed: Foo::len

class Foo {
    fun <caret>len(x: Int) = x
}

val unbound: (Foo, Int) -> Int = Foo::len
