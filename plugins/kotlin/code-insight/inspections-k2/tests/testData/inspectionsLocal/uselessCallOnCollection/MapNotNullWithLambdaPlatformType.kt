// PROBLEM: none
// WITH_STDLIB

val x = listOf("1").<caret>mapNotNull { java.lang.String("").replace('a', 'b') }