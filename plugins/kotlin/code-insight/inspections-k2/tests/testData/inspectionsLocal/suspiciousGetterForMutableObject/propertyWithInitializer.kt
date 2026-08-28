// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.Job

class Service {
    val job = <caret>Job()
}
