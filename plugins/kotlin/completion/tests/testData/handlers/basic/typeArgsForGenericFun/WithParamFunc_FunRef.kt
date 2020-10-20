import java.util.Comparator

class S(val a: Int)

fun intExtractor(p: S): Int = p.a

fun testInsertion() {
    val comp: Comparator<S> = Comparator.comparingInt(::intExtractor).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"