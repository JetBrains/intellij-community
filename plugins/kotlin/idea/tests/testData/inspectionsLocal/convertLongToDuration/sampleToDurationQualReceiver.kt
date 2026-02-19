// WITH_COROUTINES

import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds

fun test(flow: Flow<Int>) {
    flow.sa<caret>mple(200)
}