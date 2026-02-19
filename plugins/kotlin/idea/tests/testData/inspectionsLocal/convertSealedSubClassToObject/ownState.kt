// PROBLEM: none
// WITH_STDLIB

sealed class SC {
    <caret>class U : SC() {
        val a = mutableListOf<String>()
    }
}