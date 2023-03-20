fun test() {
    (consume(listOf(1, 2, 3)))
}

fun <T> consume(list: List<T>) {}