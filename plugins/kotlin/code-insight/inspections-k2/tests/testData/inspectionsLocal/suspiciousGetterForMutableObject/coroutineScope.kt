// WITH_COROUTINES
// PROBLEM: Getter returns a new 'CoroutineScope' on each access
// FIX: Convert property getter to initializer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class Service {
    val scope <caret>get() = CoroutineScope(Dispatchers.Default)
}
