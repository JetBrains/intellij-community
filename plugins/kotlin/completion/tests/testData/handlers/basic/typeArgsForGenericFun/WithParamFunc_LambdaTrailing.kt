import java.util.Comparator

fun <U, V> createComparator(unused: U, keyExtractor: (V) -> String): Comparator<V> = TODO()

fun testInsertion() {
    val comp: Comparator<Int> = createComparator(10) { it.toString() }.<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"