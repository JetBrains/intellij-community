// WITH_COROUTINES

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class Test {
    suspend fun test() {
        val delayMillis = 42L
        delay(delayMi<caret>llis)
    }
}