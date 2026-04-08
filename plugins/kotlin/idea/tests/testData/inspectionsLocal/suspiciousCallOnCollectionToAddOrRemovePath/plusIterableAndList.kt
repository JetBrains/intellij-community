// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB

fun test(iterable: Iterable<String>, list: List<String>) {
    iterable <caret>+ list
}