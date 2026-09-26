// WITH_COROUTINES
// PROBLEM: Getter returns a new 'Job' on each access
// FIX: Convert property getter to initializer

import kotlinx.coroutines.Job

interface HasJob {
    val job: Job
}

class Service : HasJob {
    override val job: Job <caret>get() = Job()
}
