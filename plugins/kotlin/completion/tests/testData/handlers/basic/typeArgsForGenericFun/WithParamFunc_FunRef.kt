// FIR_IDENTICAL
// FIR_COMPARISON
import java.util.Comparator

class S(val a: Int)

fun intExtractor(p: S): Int = p.a

// <T> Comparator<T> comparingInt(ToIntFunction<? super T> keyExtractor)
// ToIntFunction<T>: int applyAsInt(T value);
// T - is inferrable from ref '::intExtractor' => (T)

fun testInsertion() {
    val comp: Comparator<S> = Comparator.comparingInt(::intExtractor).<caret>
}

// ELEMENT: reversed
// TAIL_TEXT: "()"