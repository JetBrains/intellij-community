// WITH_COROUTINES

import kotlinx.coroutines.delay

class Test {
    suspend fun test() {
        del<caret>ay(42)
    }
}