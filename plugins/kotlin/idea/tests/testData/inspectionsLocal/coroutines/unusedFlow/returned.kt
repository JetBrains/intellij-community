// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow

fun test(): Flow<Int> {
    return flowOf<caret>(1)
}