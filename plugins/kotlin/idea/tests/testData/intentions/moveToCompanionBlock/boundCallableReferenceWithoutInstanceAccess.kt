// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    fun <caret>len(x: Int) = x

    fun implicitThis(): (Int) -> Int = ::len
}

val Foo.boundThis: (Int) -> Int
    get() = this::len

fun boundParam(foo: Foo): (Int) -> Int {
    return foo::len
}
