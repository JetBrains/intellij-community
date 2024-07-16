// RUNTIME_WITH_FULL_JDK
// IGNORE_K1
import java.util.Collections

fun test() {
    val mutableList = mutableListOf(1, 2)
    Collections.<caret>sort(mutableList)
}
