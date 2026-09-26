// RUNTIME_WITH_FULL_JDK
// K2_AFTER_ERROR: DEPRECATION_ERROR

import java.util.Collections

fun test() {
    val mutableList = mutableListOf(1, 2)
    Collections.<caret>sort(mutableList, { a, b -> a.compareTo(b) })
}
