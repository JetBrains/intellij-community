// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.Job

class Service {
    private val stored = Job()

    val job <caret>get() = stored
}
