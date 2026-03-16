// WITH_COROUTINES

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class Test {
    suspend fun test() {
        delay(4<caret>2)
    }
}