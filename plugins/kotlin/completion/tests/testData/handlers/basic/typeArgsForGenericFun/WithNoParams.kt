fun <T> create(): List<T> = listOf()
fun test() {
    val create: List<Int> = create().<caret>
}

// ELEMENT: subList