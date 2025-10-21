// WITH_STDLIB

fun test(list: List<String>): List<String> {
    return list
        .groupBy { it.length }
        .filter<caret> { it.value.size > 1 }
        .map { entry -> entry.value.joinToString() }
        .map { it + it }
        .map { it + it + it }
}