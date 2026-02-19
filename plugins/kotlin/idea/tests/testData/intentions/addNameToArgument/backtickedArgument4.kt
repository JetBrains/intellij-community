// PRIORITY: LOW
// AFTER-WARNING: Parameter '1' is never used
// AFTER-WARNING: Parameter 'a' is never used
fun foo(`1`: Int, `a`: Int) {
}

fun main() {
    foo(1, 2<caret>)
}