// ERROR: No value passed for parameter 'p'
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'p' is never used
// AFTER-WARNING: Parameter 's' is never used

fun foo(s: String, b: Boolean, p: Int){}

fun bar() {
    foo("", <caret>true)
}