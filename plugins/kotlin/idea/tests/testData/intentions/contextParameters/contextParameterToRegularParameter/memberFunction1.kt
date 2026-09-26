// COMPILER_ARGUMENTS: -Xcontext-parameters

class Foo {
    context(c1: Int, c2: String<caret>)
    fun bar() {}
}

context(c1: String, c2: Int)
fun boo(foo: Foo) {
    foo.bar()
}
