// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow

fun consumer(a: Flow<Int>) {

}

suspend fun test() {
    consumer(flowOf<caret>(1))
}