// PROBLEM: none
// WITH_STDLIB

fun test(items: Iterable<Int>) {
    items.count { it > 0 } != 0<caret>
}