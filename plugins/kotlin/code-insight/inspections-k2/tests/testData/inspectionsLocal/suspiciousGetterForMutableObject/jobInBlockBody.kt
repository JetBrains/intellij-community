// WITH_COROUTINES
// PROBLEM: Getter returns a new 'Job' on each access
// FIX: Convert property getter to initializer

import kotlinx.coroutines.Job

class Service {
    val job: Job
        <caret>get() {
            return Job()
        }
}
