// WITH_STDLIB
// AFTER-WARNING: Variable 'i' is never used
// AFTER-WARNING: Variable 's' is never used
fun test() {
    mapOf(1 to "1", 2 to "2")<caret>
}