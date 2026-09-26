// WITH_COROUTINES
// PROBLEM: none

class Service {
    val defaults <caret>get() = mutableMapOf("retries" to 3)
}
