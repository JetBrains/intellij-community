// PRIORITY: LOW
// AFTER-WARNING: Parameter '1' is never used
// AFTER-WARNING: Parameter '2a' is never used
fun foo(`1`: Int, `2a`: Int) {
}

fun main() {
    foo(1, 2<caret>)
}