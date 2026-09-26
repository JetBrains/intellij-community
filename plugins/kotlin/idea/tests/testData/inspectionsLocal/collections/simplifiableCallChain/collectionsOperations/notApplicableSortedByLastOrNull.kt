// PROBLEM: none
// WITH_STDLIB
val list = listOf(1, 2, null)
val x = list.sortedBy<caret> { it }.lastOrNull()
