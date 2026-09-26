// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Service(private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            try {
                load()
            } catch (<caret>e: Exception) {
            }
        }
    }

    private suspend fun load() {
    }
}
