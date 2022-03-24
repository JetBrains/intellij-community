// PROBLEM: none
// WITH_STDLIB

fun plusNullable(arg: String?) = arg?.let<caret> { it + "#" } ?: ""