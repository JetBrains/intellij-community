import java.util.Comparator

fun <U, V> createComparator(keyExtractor: (V) -> String = { it.toString() }, unused: U): Comparator<V> = TODO()

fun testInsertion() {
    val comp: Comparator<Int> = createComparator(unused = 10).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"