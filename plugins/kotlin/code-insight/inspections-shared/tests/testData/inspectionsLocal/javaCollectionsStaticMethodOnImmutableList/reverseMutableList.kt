// WITH_STDLIB
// PROBLEM: none
import java.util.Collections

fun test() {
    val mutableList = mutableListOf(1, 2)
    Collections.reverse<caret>(mutableList)
}
