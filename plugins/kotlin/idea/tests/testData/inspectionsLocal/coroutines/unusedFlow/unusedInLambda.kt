// PROBLEM: Flow is constructed but not used
// FIX: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun test() {
    val a = listOf(1).forEach {
        flowOf(1).map<caret> { it * 2 }
    }
}