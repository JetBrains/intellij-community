// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB

fun test(list: List<String>, iterable: Iterable<String>) {
    list <caret>+ iterable
}