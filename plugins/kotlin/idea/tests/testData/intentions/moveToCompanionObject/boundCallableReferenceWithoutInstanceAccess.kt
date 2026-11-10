class Foo {
    fun <caret>len(x: Int) = x
}

fun boundParam(foo: Foo): (Int) -> Int = foo::len
