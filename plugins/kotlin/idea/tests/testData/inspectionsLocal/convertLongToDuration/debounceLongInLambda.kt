// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

fun foo(flow: Flow<Int>): Flow<Int> {
    return flow.deboun<caret>ce {
        100L
    }
}