import java.util.Comparator
import java.util.function.ToIntFunction

class S(val a: Int)

fun testInsertion() {
    val intFunc: ToIntFunction<S> = { it.a }
    val comp: Comparator<S> = Comparator.comparingInt(intFunc).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"