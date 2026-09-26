// WITH_COROUTINES
// PROBLEM: Getter returns a new 'Mutex' on each access
// FIX: Convert property getter to initializer

import kotlinx.coroutines.sync.Mutex

class Service {
    val lock <caret>get() = Mutex()
}
