// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow

fun consumer(a: Flow<Int>) {

}

suspend fun test() {
    consumer(flowOf<caret>(1))
}