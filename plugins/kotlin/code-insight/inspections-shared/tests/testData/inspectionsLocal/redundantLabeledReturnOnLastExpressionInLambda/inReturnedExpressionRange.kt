// WITH_STDLIB
// PROBLEM: none

fun foo() {
    listOf(1,2,3).find {
        return@find <caret>true
    }
}