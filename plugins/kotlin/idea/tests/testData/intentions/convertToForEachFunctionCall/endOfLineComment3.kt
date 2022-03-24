// WITH_STDLIB
// AFTER-WARNING: Parameter 'l' is never used, could be renamed to _
fun foo() {}

fun test() {
    <caret>for (l in listOf(1, 2)) {
        foo(); foo() // comment
    }
}
