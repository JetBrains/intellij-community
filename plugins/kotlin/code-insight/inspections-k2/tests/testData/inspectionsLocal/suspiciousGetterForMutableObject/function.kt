// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.Job

class Service {
    fun <caret>newJob() = Job()
}
