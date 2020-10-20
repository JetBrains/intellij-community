import java.util.Comparator

class S(val a: Int)

fun testInsertion() {
    val comp: Comparator<S> = Comparator.comparingInt({ it.a }).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"