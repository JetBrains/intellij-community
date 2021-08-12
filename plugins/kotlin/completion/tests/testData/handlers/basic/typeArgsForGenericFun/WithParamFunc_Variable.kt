// FIR_COMPARISON
import java.util.Comparator
import java.util.function.ToIntFunction

class S(val a: Int)

// <T> Comparator<T> comparingInt(ToIntFunction<? super T> keyExtractor)
// T - is inferrable from 'val intFunc: ToIntFunction<S>' => (T)

fun testInsertion() {
    val intFunc: ToIntFunction<S> = { it.a }
    val comp: Comparator<S> = Comparator.comparingInt(intFunc).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"