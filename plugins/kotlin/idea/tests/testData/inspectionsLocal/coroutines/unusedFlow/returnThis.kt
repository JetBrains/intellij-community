// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.Flow

fun Flow<Int>.test(): Flow<Int> {
    return this<caret>
}