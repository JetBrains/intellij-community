// PROBLEM: none
// RUNTIME_WITH_FULL_JDK
import java.util.Arrays

fun test() {
    val a = arrayOf(1, 2, 3)
    val b = arrayOf(1, 2, 3)
    val result = Arrays.<caret>equals(a, 1, 2, b, 1, 2)
}