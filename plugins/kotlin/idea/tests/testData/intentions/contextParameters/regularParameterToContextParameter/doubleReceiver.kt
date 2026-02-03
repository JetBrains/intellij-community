// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Foo {
    fun String.foo(<caret>p1: Int) {}
}

fun Foo.bar(a: Any) {
    a.toString().foo(1)
}

fun baz(f: Foo) {
    with(f) {
        "baz".foo(2)
    }
}
