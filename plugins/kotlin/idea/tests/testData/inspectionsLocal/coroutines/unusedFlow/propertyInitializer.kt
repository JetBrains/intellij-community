// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun test() {
    val a = flowOf<caret>(1).map { it * 2 }
}