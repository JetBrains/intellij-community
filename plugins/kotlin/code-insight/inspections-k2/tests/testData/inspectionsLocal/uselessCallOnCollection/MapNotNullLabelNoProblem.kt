// PROBLEM: none
// WITH_STDLIB

val a = listOf(listOf(1, 2, 3, null)).<caret>mapNotNull { bar ->
    if (bar.isEmpty()) return@mapNotNull null
    bar
}