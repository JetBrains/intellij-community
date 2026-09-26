// WITH_COROUTINES

import kotlinx.coroutines.delay

class Test {
    suspend fun test() {
        val base = 100L
        del<caret>ay(base + 100)
    }
}