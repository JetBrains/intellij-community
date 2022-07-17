// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used, could be renamed to _
fun foo(f: (Int, String, Int) -> String) {}

fun test() {
    foo <caret>{ _, _, i -> "" }
}