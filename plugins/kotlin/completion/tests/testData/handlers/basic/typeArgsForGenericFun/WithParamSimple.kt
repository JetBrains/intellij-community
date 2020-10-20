fun <T> create(param: List<T>): List<T> = param
fun test() {
    create(listOf("1")).<caret>
}

// ELEMENT: subList