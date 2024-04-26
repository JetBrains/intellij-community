// "Convert receiver to parameter" "true"
// IGNORE_K2
expect class Foo {
    fun <caret>String.foo(n: Int)
}

fun Foo.test() {
    "1".foo(2)
}