// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 's' is never used
fun foo(s: String, b: Boolean){}

fun bar() {
    foo("", b = <caret>true)
}