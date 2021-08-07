fun <T> create(): List<T> = listOf()

// T - non inferrable (return value type is not taken into account)

fun test() {
    val create: List<Int> = create().<caret>
}

// ELEMENT: subList