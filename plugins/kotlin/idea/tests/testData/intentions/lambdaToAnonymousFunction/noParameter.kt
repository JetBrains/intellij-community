// AFTER-WARNING: Parameter 'f' is never used
fun foo(f: () -> String) {}

fun test() {
    foo { <caret>-> "" }
}