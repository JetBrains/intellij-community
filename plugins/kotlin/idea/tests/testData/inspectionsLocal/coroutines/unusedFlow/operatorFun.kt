
// PROBLEM: Flow is constructed but not used
// FIX: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

operator fun Flow<Int>.plus(other: Flow<Int>): Flow<Int> {
    return flowOf(1, 2, 3)
}

suspend fun foo() {
    flowOf<caret>(1, 2, 3) + flowOf(1, 2, 3)
}