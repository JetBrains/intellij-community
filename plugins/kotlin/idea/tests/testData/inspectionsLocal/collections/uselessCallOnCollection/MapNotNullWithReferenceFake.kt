// PROBLEM: none
// WITH_STDLIB

fun String.toNullableInt(): Int? = null

val x = listOf("1").<caret>mapNotNull(String::toNullableInt)