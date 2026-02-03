// WITH_STDLIB
// PROBLEM: Call of Java mutator 'reverse' on immutable Kotlin collection 'immutableList'
// FIX: none
import java.util.Collections

fun test() {
    val immutableList = listOf(1, 2)
    Collections.reverse<caret>(immutableList)
}
