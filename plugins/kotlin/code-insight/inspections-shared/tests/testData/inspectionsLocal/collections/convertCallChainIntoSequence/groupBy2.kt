// PROBLEM: none
// WITH_STDLIB
fun test(list: List<String>): Map<Int, List<String>> {
    return list
        .groupBy { it.length }
        .filter<caret> { it.value.size > 1 }
        .filter { it.value.size > 2 }
        .filter { it.value.size > 3 }
        .filter { it.value.size > 4 }
}