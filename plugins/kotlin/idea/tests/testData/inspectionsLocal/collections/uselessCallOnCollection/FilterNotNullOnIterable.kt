// WITH_STDLIB

fun test(iterable: Iterable<Int>): List<Int> {
    return iterable.<caret>filterNotNull()
}