// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.reduce

suspend fun test() {
    flow<caret> {
        emit(5)
    }.reduce { a, b -> a + b }
}