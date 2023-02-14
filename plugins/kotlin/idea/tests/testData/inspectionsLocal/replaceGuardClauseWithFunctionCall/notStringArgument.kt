// PROBLEM: none
// WITH_STDLIB
fun test() {
    try {
    } catch (e: Exception) {
        <caret>if (e is RuntimeException) throw IllegalStateException(e)
    }
}