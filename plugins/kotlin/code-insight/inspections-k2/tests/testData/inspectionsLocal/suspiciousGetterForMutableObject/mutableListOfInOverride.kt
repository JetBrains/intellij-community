// WITH_COROUTINES
// PROBLEM: none

interface HasItems {
    val items: MutableList<String>
}

class Service : HasItems {
    override val items: MutableList<String> <caret>get() = mutableListOf()
}
