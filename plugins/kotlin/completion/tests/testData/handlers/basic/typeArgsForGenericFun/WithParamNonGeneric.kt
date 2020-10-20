fun <T> create(param: Int): List<T> = listOf()
fun test() {
    val create: List<Int> = create(5).<caret>
}

// ELEMENT: subList