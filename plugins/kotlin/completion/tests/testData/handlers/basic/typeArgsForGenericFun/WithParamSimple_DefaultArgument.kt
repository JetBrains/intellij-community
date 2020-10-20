fun <T: Number> create(param: List<T> = listOf(1 as T)): List<T> = TODO()
fun test() {
    val list: List<Int> = create().<caret>
}

// ELEMENT: subList