// PRIORITY: LOW
// AFTER-WARNING: Redundant spread (*) operator
// AFTER-WARNING: Parameter 's' is never used
fun foo(vararg s: String){}

fun bar(array: Array<String>) {
    foo(<caret>*array)
}