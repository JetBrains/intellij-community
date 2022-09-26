import java.util.Comparator

class S(val a: Int)

// <T> Comparator<T> comparingInt(ToIntFunction<? super T> keyExtractor)
// T - is not inferrable from { it.a } => ()

fun testInsertion() {
    val comp: Comparator<S> = Comparator.comparingInt({ it.a }).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"