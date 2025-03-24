// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun test(): Flow<Int> {
    val a = flowOf(1)
    return (a<caret>)
}