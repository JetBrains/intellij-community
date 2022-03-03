// PROBLEM: none
// WITH_STDLIB

fun foo() {
    val y = listOf(object {
        val <caret>value = 0
    })[0]
    y.value
}