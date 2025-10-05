// PROBLEM: none
// WITH_STDLIB

fun test(list: List<Int>): List<Int> {
    return list
            .<caret>filter { it > 1 }
            .mapNotNull {
                if (it == 2) return emptyList()
                it * 2
            }
}