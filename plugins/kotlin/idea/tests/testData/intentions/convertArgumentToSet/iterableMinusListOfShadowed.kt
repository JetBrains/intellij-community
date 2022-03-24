// WITH_STDLIB
// AFTER-WARNING: Variable 'c' is never used

fun <E> listOf(vararg elements: E): List<E> {
    val result = ArrayList<E>()
    var num = 0
    for (elem in elements) {
        num++
        for (i in 1..num) {
            result.add(elem)
        }
    }

    return result
}

fun <T : CharSequence> foo(a: Iterable<T>) {
    val b = listOf("a", "b", "c", "e")
    val c = a - <caret>b
}
