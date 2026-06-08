// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

suspend fun test() {
    flow {
        emit(5)
    }.map { it * 2 }.collect<caret> { println(it) }
}