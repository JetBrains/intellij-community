// WITH_COROUTINES
// PROBLEM: Getter returns a new mutable collection on each access
// FIX: Convert property getter to initializer

class Service {
    val items <caret>get() = hashMapOf<String, Int>()
}
