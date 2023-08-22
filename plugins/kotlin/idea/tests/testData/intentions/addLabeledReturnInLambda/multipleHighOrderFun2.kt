// WITH_STDLIB
// INTENTION_TEXT: "Add 'return@bar'"
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used

private fun <T> List<T>.bar(a: (T) -> Boolean, b: (T) -> Boolean) {}

fun foo() {
    listOf(1,2,3).bar({
        <caret>true
    }) {
        true
    }
}