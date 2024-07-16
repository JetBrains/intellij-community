// RUNTIME_WITH_FULL_JDK
// PROBLEM: none
import java.util.Collections

fun test() {
    val mutableList = listOf(1, 2)
    Collections.<caret>shuffle(mutableList)
}
