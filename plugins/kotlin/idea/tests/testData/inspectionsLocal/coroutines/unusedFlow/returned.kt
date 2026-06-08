// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow

fun test(): Flow<Int> {
    return flowOf<caret>(1)
}