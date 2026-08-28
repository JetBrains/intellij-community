// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.Job

class Service {
    var job: Job = Job()
        <caret>set(value) {
            field = value
        }
}
