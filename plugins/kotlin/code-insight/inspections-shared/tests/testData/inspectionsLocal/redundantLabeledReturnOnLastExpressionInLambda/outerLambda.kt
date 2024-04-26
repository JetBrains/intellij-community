// WITH_STDLIB
// PROBLEM: none

fun foo() {
    listOf(1,2,3).forEach {
        listOf(1,2,3).find {
            <caret>return@forEach
        }
    }
}