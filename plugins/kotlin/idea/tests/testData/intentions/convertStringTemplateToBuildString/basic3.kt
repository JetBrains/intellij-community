// PRIORITY: LOW
// WITH_STDLIB
// AFTER-WARNING: Variable 's' is never used
fun test(foo: String, bar: Int) {
    val s = <caret>"${foo}${bar}aaa\nbbb"
}