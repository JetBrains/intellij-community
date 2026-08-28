// WITH_COROUTINES
// PROBLEM: Getter returns a new 'Semaphore' on each access
// FIX: Convert property getter to initializer

import kotlinx.coroutines.sync.Semaphore

class Service {
    val limit <caret>get() = Semaphore(1)
}
