// WITH_COROUTINES
// PROBLEM: none

class Service {
    val items <caret>get() = mutableListOf("a", "b")
}
