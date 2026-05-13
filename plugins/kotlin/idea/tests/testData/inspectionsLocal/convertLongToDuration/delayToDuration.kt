// WITH_COROUTINES

import kotlinx.coroutines.delay

class Test {
    suspend fun test() {
        delay(4<caret>2)
    }
}