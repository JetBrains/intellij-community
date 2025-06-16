// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

fun test(a: Flow<Int>): Flow<Int> {
    return a.flatMapLatest {
        return@flatMapLatest<caret> a
    }
}