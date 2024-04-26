// "Convert receiver to parameter" "true"
// IGNORE_K2

actual class Foo {
    actual fun <caret>String.foo(n: Int) {

    }
}

fun Foo.test1() {
    "1".foo(2)
}