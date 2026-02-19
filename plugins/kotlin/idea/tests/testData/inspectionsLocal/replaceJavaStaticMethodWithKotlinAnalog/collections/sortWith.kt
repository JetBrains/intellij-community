// RUNTIME_WITH_FULL_JDK
// IGNORE_K1
// K2_AFTER_ERROR: 'fun <T> MutableList<T>.sort(comparison: (T, T) -> Int): Unit' is deprecated. Use sortWith(Comparator(comparison)) instead.
import java.util.Collections

fun test() {
    val mutableList = mutableListOf(1, 2)
    Collections.<caret>sort(mutableList, { a, b -> a.compareTo(b) })
}
