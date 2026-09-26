// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: Add explicit labeled receiver (does not change semantics)
package test

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

class TestScope(override val coroutineContext: CoroutineContext) : CoroutineScope

private suspend fun suspendWrapper(action: suspend () -> Unit) {
    action()
}

suspend fun TestScope.test() {
    suspendWrapper {
        <caret>coroutineContext
    }
}
