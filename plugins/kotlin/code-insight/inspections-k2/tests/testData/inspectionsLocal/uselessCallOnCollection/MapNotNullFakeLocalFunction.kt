// PROBLEM: none
// WITH_STDLIB

fun List<String>.mapNotNull(f: (String) -> Int) = Unit

val x = listOf("1").<caret>mapNotNull { it.toInt() }