// WITH_STDLIB
fun test(foo: List<Int?>): List<Int> {
    return foo
        .<caret>mapNotNull { it }
        .flatMap { listOf(it, it) }
        .distinct()
        .toList()
}
