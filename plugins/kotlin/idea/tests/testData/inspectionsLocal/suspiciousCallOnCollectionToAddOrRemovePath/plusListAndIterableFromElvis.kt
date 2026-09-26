// PROBLEM: none

// WITH_STDLIB

fun test(list: List<String>, nullableList: List<String>?) {
    list.filter { it.isNotEmpty() } <caret>+ (nullableList ?: emptyList())
}