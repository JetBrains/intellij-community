// PRIORITY: LOW
// AFTER-WARNING: Parameter 'first' is never used
// AFTER-WARNING: Parameter 'last' is never used
// AFTER-WARNING: Parameter 'second' is never used
fun foo(first: Int, second: Boolean, last: String) {}

fun test() {
    foo(1, <caret>true, "")
}