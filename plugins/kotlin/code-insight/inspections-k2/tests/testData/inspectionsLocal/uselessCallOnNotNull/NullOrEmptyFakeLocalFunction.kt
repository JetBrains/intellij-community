// PROBLEM: none
// WITH_STDLIB

fun List<Int>.isNullOrEmpty(): Boolean = false
val empty = listOf(1)<caret>.isNullOrEmpty()