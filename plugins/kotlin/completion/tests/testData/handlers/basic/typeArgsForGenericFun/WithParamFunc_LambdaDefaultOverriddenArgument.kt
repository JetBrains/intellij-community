import java.util.Comparator

fun <U, V> createComparator(unused: U, keyExtractor: (V) -> String = { it.toString() }): Comparator<V> = TODO()

fun testInsertion() {
    val comp: Comparator<Int> = createComparator(10, Int::toString).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"