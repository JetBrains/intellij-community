// WITH_STDLIB
// AFTER-WARNING: Parameter 'foo' is never used
// AFTER-WARNING: Variable 's' is never used
fun test(foo: String) {
    val s = <caret>"aaa\nbbb"
}