// PROBLEM: Flow is constructed but not used
// FIX: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf

fun test() {
    flowOf<caret>(1)
}