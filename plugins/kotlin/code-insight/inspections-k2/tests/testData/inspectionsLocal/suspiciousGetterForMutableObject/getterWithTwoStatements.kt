// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.Job

class Service {
    val job: Job
        <caret>get() {
            println("creating a job")
            return Job()
        }
}
