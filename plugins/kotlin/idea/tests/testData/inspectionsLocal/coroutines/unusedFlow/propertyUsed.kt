// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

suspend fun test() {
    val a = flowOf(1)
    a.map { it * 2}.collect<caret> { }
}