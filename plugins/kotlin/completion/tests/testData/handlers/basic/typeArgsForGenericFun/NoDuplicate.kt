// FIR_COMPARISON
fun <T> create(): List<T> = listOf()
fun test() {
    val create: List<Int> = create<Int>().<caret>
}

// ELEMENT: subList