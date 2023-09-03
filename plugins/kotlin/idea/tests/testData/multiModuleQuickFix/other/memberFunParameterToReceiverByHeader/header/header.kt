// "Convert parameter to receiver" "true"
// IGNORE_K2

expect class Foo {
    fun foo(n: Int, <caret>s: String)
}

fun Foo.test() {
    foo(1, "2")
}