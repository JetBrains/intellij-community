// WITH_STDLIB

fun bar(s: String) = s.length

val x = listOf("Jack", "Tom").mapTo(kotlin.collections.hashSetOf<Int>()) <caret>{ w -> bar(w) }