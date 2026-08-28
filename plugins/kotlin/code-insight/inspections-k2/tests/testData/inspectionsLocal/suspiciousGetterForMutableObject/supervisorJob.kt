// WITH_COROUTINES
// PROBLEM: Getter returns a new 'SupervisorJob' on each access
// FIX: Convert property getter to initializer

import kotlinx.coroutines.SupervisorJob

class Service {
    val job <caret>get() = SupervisorJob()
}
