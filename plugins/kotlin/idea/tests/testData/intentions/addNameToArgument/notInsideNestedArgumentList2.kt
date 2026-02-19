// PRIORITY: LOW
// AFTER-WARNING: Parameter 'p' is never used
fun foo(p: Int){}

fun bar() {
    foo("".hashCode<caret>())
}