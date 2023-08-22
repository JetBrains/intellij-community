// PROBLEM: none
// WITH_STDLIB

fun test(list: List<Int>): List<Int> {
    return list
        .filter { it > 2 }
        .dropLast(1)
        .<caret>filter { it > 2 }
}