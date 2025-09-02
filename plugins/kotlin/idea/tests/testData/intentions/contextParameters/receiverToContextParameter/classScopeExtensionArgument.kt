// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Foo

context(c: Int)
fun <caret>Foo.foo(param: String) {
}

class Bar : Foo {
    context(c1: Int)
    fun baz() {
        foo("boo")
    }
}
