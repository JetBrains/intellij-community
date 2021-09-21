// WITH_RUNTIME
fun test() {
    <caret>for (l in listOf(1, 2)) {
        // comment
// AFTER-WARNING: Parameter 'l' is never used, could be renamed to _
    }
}
