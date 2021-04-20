import java.util.Comparator

fun <U, V> createComparator(unused: U, keyExtractor: (V) -> String): Comparator<V> = TODO()

// U - inferrable from 'unused' => (U)
// (V) -> String - V is not inferrable neither from (U) nor '{ it.toString() }'

fun testInsertion() {
    val comp: Comparator<Int> = createComparator(10) { it.toString() }.<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"