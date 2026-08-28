// WITH_COROUTINES
// PROBLEM: Getter returns a new 'Job' on each access
// FIX: none

import kotlinx.coroutines.Job

interface HasJob {
    val job: Job <caret>get() = Job()
}
