// AFTER-WARNING: Parameter 's' is never used
fun foo(s: String) {}

val s = <caret>Sam {
    foo(it)
}