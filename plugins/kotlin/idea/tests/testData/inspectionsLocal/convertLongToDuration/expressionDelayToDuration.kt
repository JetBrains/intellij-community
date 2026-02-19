// WITH_COROUTINES

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class Test {
    suspend fun test() {
        val base = 100L
        delay(ba<caret>se + 100)
    }
}