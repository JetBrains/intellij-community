// PROBLEM: Flow is constructed but not used
// FIX: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.collect

suspend fun test() {
    flowOf(1).flatMapConcat {
        flowOf<caret>(1)
        flowOf(1)
    }.collect {

    }
}