// PROBLEM: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.Flow

fun Flow<Int>.test(): Flow<Int> {
    return this<caret>
}