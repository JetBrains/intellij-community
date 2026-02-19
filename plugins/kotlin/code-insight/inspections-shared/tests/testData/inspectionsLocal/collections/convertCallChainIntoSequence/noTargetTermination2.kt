// WITH_STDLIB

fun test(): Int {
    return listOf(1, 2, 3)
        .<caret>filter { it > 1 }
        .map { it * 2 }
        .let {
            it.binarySearch(1)
        }
}