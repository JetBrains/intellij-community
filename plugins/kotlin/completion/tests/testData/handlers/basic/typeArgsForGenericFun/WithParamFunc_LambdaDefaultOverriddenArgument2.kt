// FIR_COMPARISON
import java.util.Comparator

fun <U, V> createComparator(unused: U, keyExtractor: (V) -> String = { it.toString() }): Comparator<V> = TODO()

// U - inferrable from 'unused' => (U)
// (V) -> String - V is inferrable from '{ i: Int -> i.toString() }' as Int => (U, V)

fun testInsertion() {
    val comp: Comparator<Int> = createComparator(10, { i: Int -> i.toString() } ).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"