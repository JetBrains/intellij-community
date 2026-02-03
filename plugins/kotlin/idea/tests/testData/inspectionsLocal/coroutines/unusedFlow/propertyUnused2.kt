// PROBLEM: Flow is constructed but not used
// FIX: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf

suspend fun test() {
    val a = flowOf(1)
    a<caret>
}