// AFTER-WARNING: Parameter 'any' is never used
// AFTER-WARNING: Parameter 'callback' is never used
fun <T> <caret>Any.foo(callback: () -> Unit) {}

fun bar() {
    "".foo<Int> {}
}