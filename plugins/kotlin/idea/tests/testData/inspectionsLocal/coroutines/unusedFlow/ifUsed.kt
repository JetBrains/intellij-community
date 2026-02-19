// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.flowOf

suspend fun test() {
    val a = if (1 == 1) {
        flowOf<caret>(1)
    } else {
        flowOf(2)
    }
}