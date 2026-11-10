// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: Callable reference won't be processed: ::len
// SHOULD_FAIL_WITH: Callable reference won't be processed: foo::len
// SHOULD_FAIL_WITH: Callable reference won't be processed: this::len

class Foo {
    fun <caret>len(x: Int) = this.hashCode() + x

    fun implicitThis(): (Int) -> Int = ::len
}

val Foo.boundThis: (Int) -> Int
    get() = this::len

fun boundParam(foo: Foo): (Int) -> Int {
    return foo::len
}
