// "Convert parameter to receiver" "true"
// IGNORE_K2

actual class Foo {
    actual fun foo(n: Int, <caret>s: String) {

    }
}

fun Foo.testJs() {
    foo(1, "2")
}