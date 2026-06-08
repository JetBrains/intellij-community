// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun test() {
    val a = flowOf<caret>(1).map { it * 2 }
}