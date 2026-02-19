fun test() {
    consume(listOf(1, 2, 3))<caret>
}

fun <T> consume(list: List<T>) {}