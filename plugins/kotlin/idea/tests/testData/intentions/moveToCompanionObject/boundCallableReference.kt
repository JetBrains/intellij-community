// SHOULD_FAIL_WITH: Callable reference won't be processed: foo::len

class Foo {
    fun <caret>len(x: Int) = hashCode() + x
}

fun unboundParam(foo: Foo): (Int) -> Int = foo::len
