// WITH_COROUTINES

import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.Flow

fun test(flow: Flow<Int>) {
    flow.sa<caret>mple(200)
}