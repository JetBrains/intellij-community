fun <T> create(param: Int): List<T> = listOf()

// T - is not inferrable neither from 'param' (return type value is not taken into account)

fun test() {
    val create: List<Int> = create(5).<caret>
}

// ELEMENT: subList