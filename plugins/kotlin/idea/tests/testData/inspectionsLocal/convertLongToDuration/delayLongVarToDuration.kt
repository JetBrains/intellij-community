// WITH_COROUTINES

import kotlinx.coroutines.delay

class Test {
    suspend fun test() {
        val delayMillis = 42L
        d<caret>elay(delayMillis)
    }
}