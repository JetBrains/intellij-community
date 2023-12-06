// WITH_STDLIB

fun test(list: List<String>): Map<Int, List<String>> {
    return list
        .filter<caret> { it.length > 1 }
        .filter { it.length > 2 }
        .filter { it.length > 3 }
        .groupBy { it.length }
        .filter { it.value.size > 4 }
        .filter { it.value.size > 5 }
        .filter { it.value.size > 6 }
}