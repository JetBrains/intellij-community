// RUNTIME_WITH_FULL_JDK
// IGNORE_K1
import java.util.*

fun test () {
    val list: LinkedList<String> = LinkedList()
    Collections.<caret>sort(list)
}